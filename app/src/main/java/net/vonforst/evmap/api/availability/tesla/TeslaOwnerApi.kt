package net.vonforst.evmap.api.availability.tesla

import android.net.Uri
import android.util.Base64
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalTime

interface TeslaAuthenticationApi {
    @POST("oauth2/v3/token")
    suspend fun getToken(@Body request: OAuth2Request): OAuth2Response

    @JsonClass(generateAdapter = true)
    class AuthCodeRequest(
        val code: String,
        @Json(name = "code_verifier") val codeVerifier: String,
        @Json(name = "redirect_uri") val redirectUri: String = "https://auth.tesla.com/void/callback",
        scope: String = "openid email offline_access",
        @Json(name = "client_id") clientId: String = "ownerapi"
    ) : OAuth2Request(scope, clientId)

    @JsonClass(generateAdapter = true)
    class RefreshTokenRequest(
        @Json(name = "refresh_token") val refreshToken: String,
        scope: String = "openid email offline_access",
        @Json(name = "client_id") clientId: String = "ownerapi"
    ) : OAuth2Request(scope, clientId)

    sealed class OAuth2Request(
        val scope: String,
        val clientId: String
    )

    @JsonClass(generateAdapter = true)
    data class OAuth2Response(
        @Json(name = "access_token") val accessToken: String,
        @Json(name = "token_type") val tokenType: String,
        @Json(name = "expires_in") val expiresIn: Long,
        @Json(name = "refresh_token") val refreshToken: String,
    )

    companion object {
        fun create(client: OkHttpClient, baseUrl: String? = null): TeslaAuthenticationApi {
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl ?: "https://auth.tesla.com")
                .addConverterFactory(
                    MoshiConverterFactory.create(
                        Moshi.Builder()
                            .add(
                                PolymorphicJsonAdapterFactory.of(
                                    OAuth2Request::class.java,
                                    "grant_type"
                                )
                                    .withSubtype(AuthCodeRequest::class.java, "authorization_code")
                                    .withSubtype(RefreshTokenRequest::class.java, "refresh_token")
                                    .withDefaultValue(null)
                            )
                            .build()
                    )
                )
                .client(client)
                .build()
            return retrofit.create(TeslaAuthenticationApi::class.java)
        }

        fun generateCodeVerifier(): String {
            val code = ByteArray(64)
            SecureRandom().nextBytes(code)
            return Base64.encodeToString(
                code,
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
        }

        fun generateCodeChallenge(codeVerifier: String): String {
            val bytes = codeVerifier.toByteArray()
            val messageDigest = MessageDigest.getInstance("SHA-256")
            messageDigest.update(bytes, 0, bytes.size)
            return Base64.encodeToString(
                messageDigest.digest(),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
        }

        fun buildSignInUri(codeChallenge: String): Uri =
            Uri.parse("https://auth.tesla.com/oauth2/v3/authorize").buildUpon()
                .appendQueryParameter("client_id", "ownerapi")
                .appendQueryParameter("code_challenge", codeChallenge)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("redirect_uri", "https://auth.tesla.com/void/callback")
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("scope", "openid email offline_access")
                .appendQueryParameter("state", "123").build()

        val resultUrlPrefix = "https://auth.tesla.com/void/callback"
    }
}

interface TeslaOwnerApi {
    @GET("/api/1/users/me")
    suspend fun getUserInfo(): UserInfoResponse

    @JsonClass(generateAdapter = true)
    data class UserInfoResponse(
        val response: UserInfo
    )

    @JsonClass(generateAdapter = true)
    data class UserInfo(
        val email: String,
        @Json(name = "full_name") val fullName: String,
        @Json(name = "profile_image_url") val profileImageUrl: String?
    )

    companion object {
        fun create(client: OkHttpClient, token: String, baseUrl: String? = null): TeslaOwnerApi {
            val clientWithInterceptor = client.newBuilder()
                .addInterceptor { chain ->
                    // add API key to every request
                    val request = chain.request().newBuilder()
                        .header("Authorization", "Bearer $token")
                        .header("User-Agent", "okhttp/4.9.2")
                        .header("x-tesla-user-agent", "TeslaApp/4.19.5-1667/3a5d531cc3/android/27")
                        .header("Accept", "*/*")
                        .build()
                    chain.proceed(request)
                }.build()
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl ?: "https://owner-api.teslamotors.com")
                .addConverterFactory(MoshiConverterFactory.create())
                .client(clientWithInterceptor)
                .build()
            return retrofit.create(TeslaOwnerApi::class.java)
        }
    }
}

interface TeslaChargingOwnershipGraphQlApi {
    @POST("/graphql")
    suspend fun getNearbyChargingSites(
        @Body request: GetNearbyChargingSitesRequest,
        @Query("operationName") operationName: String = "GetNearbyChargingSites",
        @Query("deviceLanguage") deviceLanguage: String = "en",
        @Query("deviceCountry") deviceCountry: String = "US",
        @Query("ttpLocale") ttpLocale: String = "en_US",
        @Query("vin") vin: String = "",
    ): GetNearbyChargingSitesResponse

    @POST("/graphql")
    suspend fun getChargingSiteInformation(
        @Body request: GetChargingSiteInformationRequest,
        @Query("operationName") operationName: String = "getChargingSiteInformation",
        @Query("deviceLanguage") deviceLanguage: String = "en",
        @Query("deviceCountry") deviceCountry: String = "US",
        @Query("ttpLocale") ttpLocale: String = "en_US",
        @Query("vin") vin: String = "",
    ): GetChargingSiteInformationResponse

    @JsonClass(generateAdapter = true)
    data class GetNearbyChargingSitesRequest(
        override val variables: GetNearbyChargingSitesVariables,
        override val operationName: String = "GetNearbyChargingSites",
        override val query: String =
            "\n    query GetNearbyChargingSites(\$args: GetNearbyChargingSitesRequestType!) {\n  charging {\n    nearbySites(args: \$args) {\n      sitesAndDistances {\n        ...ChargingNearbySitesFragment\n      }\n    }\n  }\n}\n    \n    fragment ChargingNearbySitesFragment on ChargerSiteAndDistanceType {\n  activeOutages {\n    message\n    nonTeslasAffectedOnly {\n      value\n    }\n  }\n  availableStalls {\n    value\n  }\n  centroid {\n    ...EnergySvcCoordinateTypeFields\n  }\n  drivingDistanceMiles {\n    value\n  }\n  entryPoint {\n    ...EnergySvcCoordinateTypeFields\n  }\n  haversineDistanceMiles {\n    value\n  }\n  id {\n    text\n  }\n  localizedSiteName {\n    value\n  }\n  maxPowerKw {\n    value\n  }\n  trtId {\n    value\n  }\n  totalStalls {\n    value\n  }\n  siteType\n  accessType\n  waitEstimateBucket\n  hasHighCongestion\n}\n    \n    fragment EnergySvcCoordinateTypeFields on EnergySvcCoordinateType {\n  latitude\n  longitude\n}\n    "
    ) : GraphQlRequest()

    @JsonClass(generateAdapter = true)
    data class GetNearbyChargingSitesVariables(val args: GetNearbyChargingSitesArgs)

    @JsonClass(generateAdapter = true)
    data class GetNearbyChargingSitesArgs(
        val userLocation: Coordinate,
        val northwestCorner: Coordinate,
        val southeastCorner: Coordinate,
        val openToNonTeslasFilter: OpenToNonTeslasFilterValue,
        val languageCode: String = "en",
        val countryCode: String = "US",
        //val vin: String = "",
        //val maxCount: Int = 100
    )

    @JsonClass(generateAdapter = true)
    data class OpenToNonTeslasFilterValue(val value: Boolean)

    @JsonClass(generateAdapter = true)
    data class Coordinate(val latitude: Double, val longitude: Double)

    @JsonClass(generateAdapter = true)
    data class GetChargingSiteInformationRequest(
        override val variables: GetChargingSiteInformationVariables,
        override val operationName: String = "getChargingSiteInformation",
        override val query: String =
            "\n    query getChargingSiteInformation(\$id: ChargingSiteIdentifierInputType!, \$vehicleMakeType: ChargingVehicleMakeTypeEnum, \$deviceCountry: String!, \$deviceLanguage: String!) {\n  charging {\n    site(\n      id: \$id\n      deviceCountry: \$deviceCountry\n      deviceLanguage: \$deviceLanguage\n      vehicleMakeType: \$vehicleMakeType\n    ) {\n      siteStatic {\n        ...SiteStaticFragmentNoHoldAmt\n      }\n      siteDynamic {\n        ...SiteDynamicFragment\n      }\n      pricing(vehicleMakeType: \$vehicleMakeType) {\n        userRates {\n          ...ChargingActiveRateFragment\n        }\n        memberRates {\n          ...ChargingActiveRateFragment\n        }\n        hasMembershipPricing\n        hasMSPPricing\n        canDisplayCombinedComparison\n      }\n      holdAmount(vehicleMakeType: \$vehicleMakeType) {\n        currencyCode\n        holdAmount\n      }\n      congestionPriceHistogram(vehicleMakeType: \$vehicleMakeType) {\n        ...CongestionPriceHistogramFragment\n      }\n    }\n  }\n}\n    \n    fragment SiteStaticFragmentNoHoldAmt on ChargingSiteStaticType {\n  address {\n    ...AddressFragment\n  }\n  amenities\n  centroid {\n    ...EnergySvcCoordinateTypeFields\n  }\n  entryPoint {\n    ...EnergySvcCoordinateTypeFields\n  }\n  id {\n    text\n  }\n  accessCode {\n    value\n  }\n  localizedSiteName {\n    value\n  }\n  maxPowerKw {\n    value\n  }\n  name\n  openToPublic\n  chargers {\n    id {\n      text\n    }\n    label {\n      value\n    }\n  }\n  publicStallCount\n  timeZone {\n    id\n    version\n  }\n  fastchargeSiteId {\n    value\n  }\n  siteType\n  accessType\n  isMagicDockSupportedSite\n  trtId {\n    value\n  }\n}\n    \n    fragment AddressFragment on EnergySvcAddressType {\n  streetNumber {\n    value\n  }\n  street {\n    value\n  }\n  district {\n    value\n  }\n  city {\n    value\n  }\n  state {\n    value\n  }\n  postalCode {\n    value\n  }\n  country\n}\n    \n\n    fragment EnergySvcCoordinateTypeFields on EnergySvcCoordinateType {\n  latitude\n  longitude\n}\n    \n\n    fragment SiteDynamicFragment on ChargingSiteDynamicType {\n  id {\n    text\n  }\n  activeOutages {\n    message\n    nonTeslasAffectedOnly {\n      value\n    }\n  }\n  chargersAvailable {\n    value\n  }\n  chargerDetails {\n    charger {\n      id {\n        text\n      }\n      label {\n        value\n      }\n      name\n    }\n    availability\n  }\n  waitEstimateBucket\n  currentCongestion\n}\n    \n\n    fragment ChargingActiveRateFragment on ChargingActiveRateType {\n  activePricebook {\n    charging {\n      ...ChargingUserRateFragment\n    }\n    parking {\n      ...ChargingUserRateFragment\n    }\n    priceBookID\n  }\n}\n    \n    fragment ChargingUserRateFragment on ChargingUserRateType {\n  currencyCode\n  programType\n  rates\n  buckets {\n    start\n    end\n  }\n  bucketUom\n  touRates {\n    enabled\n    activeRatesByTime {\n      startTime\n      endTime\n      rates\n    }\n  }\n  uom\n  vehicleMakeType\n}\n    \n\n    fragment CongestionPriceHistogramFragment on HistogramData {\n  axisLabels {\n    index\n    value\n  }\n  regionLabels {\n    index\n    value {\n      ...ChargingPriceFragment\n      ... on HistogramRegionLabelValueString {\n        value\n      }\n    }\n  }\n  chargingUom\n  parkingUom\n  parkingRate {\n    ...ChargingPriceFragment\n  }\n  data\n  activeBar\n  maxRateIndex\n  whenRateChanges\n  dataAttributes {\n    congestionThreshold\n    label\n  }\n}\n    \n    fragment ChargingPriceFragment on ChargingPrice {\n  currencyCode\n  price\n}\n"
    ) : GraphQlRequest()

    @JsonClass(generateAdapter = true)
    data class GetChargingSiteInformationVariables(
        val id: ChargingSiteIdentifier,
        val vehicleMakeType: VehicleMakeType,
        val deviceLanguage: String = "en",
        val deviceCountry: String = "US",
        val ttpLocale: String = "en_US"
    )

    @JsonClass(generateAdapter = true)
    data class ChargingSiteIdentifier(
        val id: String,
        val type: ChargingSiteIdentifierType = ChargingSiteIdentifierType.SITE_ID
    )

    enum class ChargingSiteIdentifierType {
        SITE_ID
    }

    enum class VehicleMakeType {
        TESLA, NON_TESLA
    }

    @JsonClass(generateAdapter = true)
    data class GetNearbyChargingSitesResponse(val data: GetNearbyChargingSitesResponseData)

    @JsonClass(generateAdapter = true)
    data class GetNearbyChargingSitesResponseData(val charging: GetNearbyChargingSitesResponseDataCharging?)

    @JsonClass(generateAdapter = true)
    data class GetNearbyChargingSitesResponseDataCharging(val nearbySites: GetNearbyChargingSitesResponseDataChargingNearbySites)

    @JsonClass(generateAdapter = true)
    data class GetNearbyChargingSitesResponseDataChargingNearbySites(val sitesAndDistances: List<ChargingSite>)

    @JsonClass(generateAdapter = true)
    data class ChargingSite(
        val activeOutages: List<Outage>,
        val availableStalls: Value<Int>?,
        val centroid: Coordinate,
        val drivingDistanceMiles: Value<Double>?,
        val entryPoint: Coordinate,
        val haversineDistanceMiles: Value<Double>,
        val id: Text,
        val localizedSiteName: Value<String>,
        val maxPowerKw: Value<Int>,
        val totalStalls: Value<Int>
        // TODO: siteType, accessType
    )

    @JsonClass(generateAdapter = true)
    data class GetChargingSiteInformationResponse(val data: GetChargingSiteInformationResponseData)

    @JsonClass(generateAdapter = true)
    data class GetChargingSiteInformationResponseData(val charging: GetChargingSiteInformationResponseDataCharging)

    @JsonClass(generateAdapter = true)
    data class GetChargingSiteInformationResponseDataCharging(val site: ChargingSiteInformation?)

    @JsonClass(generateAdapter = true)
    data class ChargingSiteInformation(
        val siteDynamic: SiteDynamic,
        val siteStatic: SiteStatic,
        val pricing: Pricing,
        val congestionPriceHistogram: CongestionPriceHistogram?,
    )

    @JsonClass(generateAdapter = true)
    data class SiteDynamic(
        val activeOutages: List<Outage>,
        val chargerDetails: List<ChargerDetail>,
        val chargersAvailable: Value<Int>?,
        val currentCongestion: Double,
        val id: Text,
        val waitEstimateBucket: WaitEstimateBucket
    )

    @JsonClass(generateAdapter = true)
    data class ChargerId(
        val id: Text,
        val label: Value<String>?,
        val name: String?
    ) {
        val labelNumber
            get() = label?.value?.replace(Regex("""\D"""), "")?.toInt()
        val labelLetter
            get() = label?.value?.replace(Regex("""\d"""), "")
    }

    @JsonClass(generateAdapter = true)
    data class ChargerDetail(
        val availability: ChargerAvailability,
        val charger: ChargerId
    )

    @JsonClass(generateAdapter = true)
    data class SiteStatic(
        val accessCode: Value<String>?,
        val centroid: Coordinate,
        val chargers: List<ChargerId>,
        val entryPoint: Coordinate,
        val fastchargeSiteId: Value<Long>,
        val id: Text,
        val isMagicDockSupportedSite: Boolean,
        val localizedSiteName: Value<String>,
        val maxPowerKw: Value<Int>,
        val name: String,
        val openToPublic: Boolean,
        val publicStallCount: Int
        // TODO: siteType, accessType, address, amenities, timeZone
    )

    @JsonClass(generateAdapter = true)
    data class CongestionPriceHistogram(
        val data: List<Double>,
        val dataAttributes: List<CongestionHistogramDataAttributes>
    )

    @JsonClass(generateAdapter = true)
    data class CongestionHistogramDataAttributes(
        val congestionThreshold: String,  // "LEVEL_1"
        val label: String  // "1AM", "2AM", etc.
    )

    @JsonClass(generateAdapter = true)
    data class Value<T : Any>(val value: T)

    @JsonClass(generateAdapter = true)
    data class Text(val text: String)

    enum class WaitEstimateBucket {
        @Json(name = "WAIT_ESTIMATE_BUCKET_NO_WAIT")
        NO_WAIT,

        @Json(name = "WAIT_ESTIMATE_BUCKET_LESS_THAN_5_MINUTES")
        LESS_THAN_5_MINUTES,

        @Json(name = "WAIT_ESTIMATE_BUCKET_APPROXIMATELY_5_MINUTES")
        APPROXIMATELY_5_MINUTES,

        @Json(name = "WAIT_ESTIMATE_BUCKET_APPROXIMATELY_10_MINUTES")
        APPROXIMATELY_10_MINUTES,

        @Json(name = "WAIT_ESTIMATE_BUCKET_APPROXIMATELY_15_MINUTES")
        APPROXIMATELY_15_MINUTES,

        @Json(name = "WAIT_ESTIMATE_BUCKET_APPROXIMATELY_20_MINUTES")
        APPROXIMATELY_20_MINUTES,

        @Json(name = "WAIT_ESTIMATE_BUCKET_GREATER_THAN_25_MINUTES")
        GREATER_THAN_25_MINUTES,

        @Json(name = "WAIT_ESTIMATE_BUCKET_UNKNOWN")
        UNKNOWN
    }

    companion object {
        fun create(
            client: OkHttpClient,
            baseUrl: String? = null,
            token: suspend () -> String
        ): TeslaChargingOwnershipGraphQlApi {
            val clientWithInterceptor = client.newBuilder()
                .addInterceptor { chain ->
                    val t = runBlocking { token() }
                    // add API key to every request
                    val request = chain.request().newBuilder()
                        .header("Authorization", "Bearer $t")
                        .header("User-Agent", "okhttp/4.9.2")
                        .header("x-tesla-user-agent", "TeslaApp/4.19.5-1667/3a5d531cc3/android/27")
                        .header("Accept", "*/*")
                        .build()
                    chain.proceed(request)
                }.build()
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl ?: "https://akamai-apigateway-charging-ownership.tesla.com")
                .addConverterFactory(
                    MoshiConverterFactory.create(
                        Moshi.Builder().add(LocalTimeAdapter()).build()
                    )
                )
                .client(clientWithInterceptor)
                .build()
            return retrofit.create(TeslaChargingOwnershipGraphQlApi::class.java)
        }
    }
}