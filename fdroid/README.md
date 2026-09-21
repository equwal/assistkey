# F-Droid

`dev.equwal.assistkey.yml` is the F-Droid metadata of Rebind. F-Droid builds
the `fdroid` flavour. That flavour has no Google Play Billing, no licence check
and no apps of other makers inside. The `rm` line removes the folder of those
apps before the build, because F-Droid does not accept prebuilt binaries.

## First submission (one time)

1. Fork `fdroid/fdroiddata` on GitLab, then clone your fork.
2. Copy `dev.equwal.assistkey.yml` into `metadata/` of your clone.
3. Run `fdroid readmeta` and `fdroid lint dev.equwal.assistkey`.
4. Commit on a new branch and push it to your fork.
5. Open a merge request against `fdroid/fdroiddata`.

## Each new version (automatic)

The metadata has `UpdateCheckMode: Tags` and `AutoUpdateMode: Version`. F-Droid
checks the tags of this repository. When it finds a new tag, it reads
`assistkey.versionName` and `assistkey.versionCode` from `gradle.properties`
at that tag and adds a build by itself.

To release a version:

1. Set `assistkey.versionName` and `assistkey.versionCode` in `gradle.properties`.
   The version code must be higher than the last one.
2. Add the entry to `CHANGELOG.md`. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
3. Commit, then tag with `v` and the version name, for example `v0.1.2-beta`. Push the tag.

The workflow `.github/workflows/tag-check.yml` runs on each `v*` tag. It fails
if the tag does not match `gradle.properties`, if the changelog file is
missing, if a test fails, or if the F-Droid build holds a bundled app or the
billing library. Fix the failure and move the tag before F-Droid picks it up.
