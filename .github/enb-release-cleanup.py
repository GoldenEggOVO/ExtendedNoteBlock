import json, subprocess, os
from pathlib import Path

repo = "GoldenEggOVO/ExtendedNoteBlock"
assert os.environ["GITHUB_REPOSITORY"] == repo
expected = json.loads(Path(".github/enb-release-cleanup.json").read_text())

def api(path, method="GET", missing=False):
    result = subprocess.run(["gh", "api", "--method", method, f"repos/{repo}/{path}"], capture_output=True, text=True)
    if result.returncode:
        if missing and "HTTP 404" in result.stderr:
            return None
        raise RuntimeError(result.stderr)
    return json.loads(result.stdout) if result.stdout.strip() else None

def fingerprint(release):
    return sorted([(a["id"], a["name"], a["size"], a["digest"]) for a in release["assets"]])

keep = api("releases/tags/" + expected["keep_tag"])
old = api("releases/tags/" + expected["old_tag"], missing=True)
assert keep["id"] == expected["keep_id"] and not keep["draft"] and not keep["prerelease"]
assert fingerprint(keep) == sorted([tuple(a) for a in expected["keep_assets"]])
assert api("git/ref/tags/" + expected["keep_tag"])["object"]["sha"] == expected["keep_sha"]
old_ref = api("git/ref/tags/" + expected["old_tag"], missing=True)
if old is not None:
    assert old["id"] == expected["old_id"] and old["tag_name"] == expected["old_tag"]
if old_ref is not None:
    assert old_ref["object"]["sha"] == expected["old_sha"]
if old is not None:
    api("releases/" + str(expected["old_id"]), method="DELETE")
if old_ref is not None:
    api("git/refs/tags/" + expected["old_tag"], method="DELETE")
after = api("releases/tags/" + expected["keep_tag"])
assert after["id"] == keep["id"] and fingerprint(after) == fingerprint(keep)
assert api("git/ref/tags/" + expected["keep_tag"])["object"]["sha"] == expected["keep_sha"]
assert api("releases/tags/" + expected["old_tag"], missing=True) is None
assert api("git/ref/tags/" + expected["old_tag"], missing=True) is None
print("Verified latest release and all assets unchanged; superseded release and tag removed")
