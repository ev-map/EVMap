import subprocess
import json

build_types = ["fossNormalRelease", "fossAutomotiveRelease"]

for build_type in build_types:
    data = json.load(
        open(f"app/build/generated/aboutLibraries/{build_type}/res/raw/aboutlibraries.json"))

    with open(f"licenses_{build_type}_appning.csv", "w") as f:
        f.write("component_name;license_title;license_url;public_repository;copyrights\n")
        for lib in data["libraries"]:
            license = data["licenses"][lib["licenses"][0]] if len(lib["licenses"]) > 0 else None
            license_name = license["name"] if license is not None else " "
            license_url = license["url"] if license is not None else " "
            copyrights = ", ".join([dev["name"] for dev in lib["developers"] if "name" in dev])
            if copyrights == "":
                copyrights = " "
            repo_url = lib['scm']['url'] if 'scm' in lib else ''
            f.write(f"{lib['name']};{license_name};{license_url};\"{copyrights}\";{repo_url}\n")
