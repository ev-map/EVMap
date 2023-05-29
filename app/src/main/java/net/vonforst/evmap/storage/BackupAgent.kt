package net.vonforst.evmap.storage

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.runBlocking
import java.time.Instant

private const val backupFileName = "evmap-backup.db"

class BackupAgent : BackupAgent() {
    override fun onBackup(
        oldState: ParcelFileDescriptor,
        data: BackupDataOutput,
        newState: ParcelFileDescriptor
    ) {
        // unused on Android M+, we don't support backups on older versions
    }

    override fun onRestore(
        data: BackupDataInput,
        appVersionCode: Int,
        newState: ParcelFileDescriptor
    ) {
        // unused on Android M+, we don't support backups on older versions
    }

    override fun onFullBackup(data: FullBackupDataOutput) {
        runBlocking {
            // creates a backup of the app database to evmap-backup.db
            AppDatabase.getInstance(applicationContext).createBackup(
                applicationContext,
                backupFileName
            )
        }
        super.onFullBackup(data)
        val backupDb = applicationContext.getDatabasePath(backupFileName)
        if (backupDb.exists()) backupDb.delete()
    }

    override fun onRestoreFinished() {
        super.onRestoreFinished()
        // rename restored backup DB as evmap.db
        val backupDb = applicationContext.getDatabasePath(backupFileName)
        if (backupDb.exists()) {
            backupDb.renameTo(applicationContext.getDatabasePath("evmap.db"))
        }
        // clear cache age
        PreferenceDataSource(applicationContext).lastGeReferenceDataUpdate = Instant.EPOCH
        PreferenceDataSource(applicationContext).lastOcmReferenceDataUpdate = Instant.EPOCH
    }
}