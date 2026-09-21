# Monetization models for Rebind

## Decision of the owner, 2026-09-21

A device with no Google Play gets the full app at no charge. The owner accepts
that some users turn Google Play off to get this. The US$9.99 licence for the
`full` build is dropped. Income comes from the Pro unlock on Google Play and
from a tip link (Ko-fi) with no feature behind it. The text below is the
research as it was written, before this decision.

## Recommendation

Keep the free core for everyone. Sell Pro through two one-time unlocks, not a
subscription. On Google Play, sell the `play` build's Pro unlock for **US$4.99**
(already coded). Off Play, sell the `full` build's licence through Lemon
Squeezy for **US$9.99**, for e-ink readers that have no Play Store. Add a
no-gate tip link (Ko-fi or GitHub Sponsors) for people who want to give more.
This combination reaches paying users and non-paying users at the same time,
which matches the goal to help people improve their devices.

## Model comparison

| Model | Real example | User pays | Policy risk | Dev effort | Revenue potential | Goodwill / reach |
|---|---|---|---|---|---|---|
| Free + Play one-time Pro IAP | Button Mapper (~US$9.99), MacroDroid (~US$4-5) | Once, in the Play build | Low on Play; no reach off Play | Low, code exists | Modest, small niche | High, free core for all |
| Paid app, free trial off Play | Tasker (Play ~US$3.49, free 7-day trial APK) | Once, after trial | None, direct download | Low, `full` build exists | Modest, reaches non-Play devices | Medium, trial ends |
| Donations / tip jar | KOReader (~€47/week, Liberapay) | Any amount, or none | None, allowed everywhere | Very low, one link | Small, uneven | Very high, nothing locked |
| Open-core, paid extras | Key Mapper (open source, paid convenience) | For extras only | Low; F-Droid lists only the FOSS part | Medium, needs a clean split | Modest | Very high, source stays open |
| Pay-what-you-want / sponsorware | Common on itch.io, Gumroad | Any amount they pick | None | Low-medium, needs a store page | Variable, often low | Very high, ends up free |

## Generous but paid

Three ways to help people and still earn money:

1. **Free forever, no trial timer:** basic remap of two buttons stays free on
   every device, always. This covers the most common hardware-hacking need.
2. **Free licence on request:** anyone who cannot pay gets a full Pro licence
   by email. Do not ask for proof. Trust the request.
3. **All e-ink profiles are free:** device profiles for Viwoods, Boox, and
   other e-ink readers stay free, because these readers are the project's
   reason to exist. Pro sells only power features, such as multi-tap,
   combinations, menus and the full Power button. Settings export and import
   stay free (see the rules below).

## Risks and rules

- **Play payments policy:** the Play-distributed APK must use Play Billing
  for purchases used inside it. Do not add an external payment link inside
  the `play` build. Sell the Lemon Squeezy licence only in the `full` build.
- **No prohibited claims:** do not call the app a "RAM booster" or "battery
  saver." These claims are false for a button remapper, and Play bans them.
- **Refunds:** Play handles its own refund window. For the Lemon Squeezy
  licence, publish one rule: refund within 14 days, no questions asked.
- **Never lock behind payment:** the control that turns off Power-button
  handling, and settings export and import. A user must always undo a bad
  remap and keep their own data.

## Launch sequence and kill criteria

1. Ship the free public beta now, on both builds. Collect the 12 testers for
   14 days that Play requires.
2. Open Play production: free core, plus the US$4.99 Pro one-time unlock.
3. At the same time, open the Lemon Squeezy US$9.99 licence for the `full`
   build. This reaches e-ink readers with no Play Store, the audience the
   owner most wants to help.
4. Add a tip link (Ko-fi or GitHub Sponsors) to the README of the public
   repository and to the Licence screen of the `full` build, with no feature
   gate attached. Do not put it in the `play` build.

**Kill criteria:** fewer than 25 paid unlocks, counting Play and Lemon
Squeezy together, in the 60 days after Play production. If this happens, drop
the paid Pro tier and keep the free app plus the tip link only.

## Sources

- [Button Mapper - Google Play](https://play.google.com/store/apps/details?id=flar2.homebutton)
- [Key Mapper - F-Droid](https://f-droid.org/packages/io.github.sds100.keymapper/)
- [Tasker - trial and pricing](https://tasker.joaoapps.com/)
- [MacroDroid pricing - Hacker News](https://news.ycombinator.com/item?id=42254890)
- [Nova Launcher Prime pricing - XDA Forums](https://xdaforums.com/t/nova-launcher-prime-is-down-to-0-99-down-from-4-99-on-the-play-store-once-again.4379115/)
- [Niagara Pro price](https://help.niagaralauncher.app/article/104-price-of-niagara-pro)
- [KOReader - Liberapay donations](https://en.liberapay.com/KOReader/donate)
- [Shizuku - GitHub](https://github.com/RikkaApps/Shizuku)
- [F-Droid Anti-Features policy](https://f-droid.org/en/docs/Anti-Features/)
- [Google Play App Licensing overview](https://developer.android.com/google/play/licensing/overview)
