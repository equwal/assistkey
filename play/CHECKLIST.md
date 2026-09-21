# Publishing AssistKey on Google Play

Everything that can be prepared without your Google account is already in this
repository. What is left needs your login, your card, or your identity, in this
order.

## 1. Open the developer account (you, once)

1. Go to <https://play.google.com/console/signup> with the Google account that
   should own the app.
2. Choose **Personal** unless you have a registered business.
3. Pay the registration fee: **US$25, one time**. (Not $5.)
4. Complete identity verification. Google asks for a government ID and a phone
   number, and the name must match the payment card. Approval takes from a few
   hours to a few days.
5. To be paid, set up a **payments profile** (Play Console > Setup > Payments
   profile) with a bank account and tax information. You cannot create a priced
   product until this exists.

A personal account created after November 2023 must run a **closed test with at
least 12 testers opted in for 14 continuous days** before Google will let the
app go to production. That is your beta; plan for it.

## 2. Create the app

Play Console > **Create app**

| Field | Value |
|---|---|
| App name | `AssistKey: E-Ink Key Remap` |
| Default language | English (United States) |
| App or game | App |
| Free or paid | **Free** (the licence is an in-app product; a paid app could not give testers a discount) |

The package name is fixed by the first upload: `dev.equwal.assistkey`.

## 3. App signing - do this before the first upload

Play re-signs every app. If it signs with a key of its own, the APK you hand to
testers and the copy Play delivers will carry different signatures. Then Play
cannot upgrade a sideloaded install in place, and purchases made from a
sideloaded install fail.

So give Play **the key this repo already signs with**:

Play Console > Test and release > Setup > **App signing** > *Use a different
key* > *Export and upload a key from Java keystore*. Download the
`encryption_public_key.pem` it offers and the PEPK tool, then:

```bash
java -jar pepk.jar --keystore=assistkey-release.jks --alias=assistkey \
  --output=assistkey-play-signing.zip --include-cert \
  --rsa-aes-encryption --encryption-key-path=encryption_public_key.pem
```

It asks for the keystore password, which is in `keystore.properties`. Upload the
zip. Expected certificate SHA-256:

```
bcb3127e1931a9b906e8e2498184ef5380f97cbf3531e5bc8bb0064476ee2919
```

**Back up `assistkey-release.jks` and `keystore.properties` somewhere safe.**
Neither is in git. Without them you cannot ship another update.

## 4. Upload the build

Test and release > Testing > **Internal testing** > Create new release > upload
`AssistKey-<version>-play.aab` (attached to the GitHub release, or rebuild with
`./gradlew bundlePlayRelease`). Upload the `play` build only. It has no Shizuku
code and no Shizuku permission. The `full` APK is for direct install.

Internal testing needs no review and is live in minutes. Products cannot be
created until one build has been uploaded.

## 5. Create the three products

Monetize with Play > Products > **One-time products**. The ids can never be
changed or reused, and the app looks for exactly these:

| Product id | Name | Price | State |
|---|---|---|---|
| `assistkey_pro` | AssistKey licence | **US$2.99** | Active |
| `assistkey_pro_tester` | AssistKey licence - beta tester price | **US$1.49** | Active |
| `assistkey_beta_open` | Beta access flag (not for sale) | US$0.99 | **Active while the beta runs** |

All three: purchase type **Buy**, non-consumable. Let Play convert the prices
to other currencies.

`assistkey_beta_open` is never offered by the app. It is only a switch - see
section 9.

Price review, 2026-09-21: the market check advises **US$4.99**, not US$2.99.
Reason: US$2.99 is the exact price of Button Mapper Pro, which has 5M+ installs,
so a price match cannot win; the e-ink features have no paid competitor. At
US$4.99 the margin is US$4.24 per sale, and US$1,000 a month needs 236 sales
and not 394. The owner has not decided. A price is a Console edit, not a release.

Pricing: Button Mapper Pro, the nearest comparable app, is US$2.99. AssistKey
serves a far smaller audience but does more on the hardware it targets, so it
matches that price rather than undercutting it. Prices are read from Play at
run time; changing one is a Console edit, not a release.

## 6. Store listing

Grow users > Store presence > **Main store listing**. Copy is in
[LISTING.md](LISTING.md); graphics are in [graphics/](graphics/).

## 7. App content declarations

Policy and programs > **App content**. Answers are in
[DECLARATIONS.md](DECLARATIONS.md). The privacy policy is
[PRIVACY.md](PRIVACY.md) and must be reachable at a public URL - this repo is
private, so it has to be hosted elsewhere (a public gist, or a page on
equwal.com).

The **accessibility declaration** is the one most likely to draw a reviewer.
The answer and the evidence for it are in DECLARATIONS.md.

## 8. Test the purchase before anyone else does

Billing could not be tested before the app existed on Play. Do this first:

1. Play Console > Setup > **License testing**: add your own Gmail address.
   License testers buy with a test card and are never charged.
2. Internal testing > Testers: add the same address, open the opt-in link on
   the reader, install from Play.
3. On the reader, Google Play must be switched on - the Viwoods firmware ships
   with it disabled.
4. Open AssistKey > the licence row. Prices should appear within a second or
   two. Buy with the test card and confirm it shows **Unlocked**.
5. Refund it from Order management to test again.

If the prices never appear, the usual causes are: products not Active, the
account not on the tester list, or the signatures not matching (section 3).

## 9. Running the beta, and ending it

**During the beta** hand testers either the Play opt-in link or
the `play` APK from the GitHub release. Both are the same build. While
`assistkey_beta_open` is Active, every install is fully unlocked for free, and
each one quietly marks itself as a tester.

**To end the beta:** set `assistkey_beta_open` to **Inactive**. Within hours,
every install that can reach Play drops to a 7-day trial and then locks.
Installs that cannot reach Play lock on the date compiled into the build,
`2027-03-31` (`assistkey.betaExpires` in `gradle.properties`).

**The tester discount** then appears by itself: an install that ran during the
beta is offered `assistkey_pro_tester` instead of `assistkey_pro`. A tester who
changed device, or had to reinstall, types the tester code instead. The code is
in `keystore.properties` (`testerCode`), never in git; only its hash ships.
Changing it means a new release.

To stop offering the discount later, deactivate `assistkey_pro_tester`.

## 10. Go to production

After the 14-day closed test, Play Console offers **Apply for production
access**. It asks about the test; answer plainly. Then promote the build to
Production and choose countries.

## Releasing an update

1. Raise `assistkey.versionCode` (must increase every upload) and
   `assistkey.versionName` in `gradle.properties`.
2. `./gradlew assembleFullRelease assemblePlayRelease bundlePlayRelease`
3. Tag it, upload the `.aab`, attach both files to a GitHub release.
