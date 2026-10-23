# RUSAKSU

KernelSU fork with its own identity — by **RSKSeven Project**, for the Asus
Zenfone Max Pro M1 (X00TD) custom ROM/kernel project.

RUSAKSU is a rebrand-and-extend fork of
[KernelSU-Next](https://github.com/KernelSU-Next/KernelSU-Next), aligned with
the RSKSeven kernel pipeline (4.4 and 4.19, non-GKI, manual-hook integration
with SuSFS). Planned additions on top of the rebrand: root-hiding features
(stealth manager, per-app deny list, mount-trace hiding, and more).

## Identity

- Manager application id: `id.rskseven.rusaksu`
- The kernel only crowns a manager APK that (1) carries this package name
  and (2) is signed with the RUSAKSU release certificate — the expected
  signature size/hash are pinned in `kernel/Kbuild`
  (`KSU_NEXT_MANAGER_SIZE` / `KSU_NEXT_MANAGER_HASH`), and the expected
  package is pinned via `KSU_MANAGER_PACKAGE` (overridable at build time).
- Kernel/userspace ABI and on-disk paths (`/data/adb/ksu`, module layout)
  are intentionally unchanged, so the existing module ecosystem keeps
  working.

## Branches

| Branch | Base | Contents |
|---|---|---|
| `dev` | upstream KernelSU-Next `dev` | upstream mirror |
| `rusaksu` | `dev` | rebranded manager + ksud + dev-line kernel |
| `ksu-4.4` | upstream `legacy` | rebranded kernel driver for X00TD kernel 4.4 |
| `ksu-4.19` | upstream `legacy` | rebranded kernel driver for kernel 4.19 |

## Building the manager

The manager release build is signed with the RUSAKSU keystore. Copy
`manager/sign.example.properties` to `manager/sign.properties` and fill in
the keystore path/passwords (the keystore itself is **not** in this repo),
then build from `manager/` with the Gradle wrapper.

## Credits & license

RUSAKSU stands on the work of the KernelSU and KernelSU-Next authors and
contributors — thank you. Licensed under the GNU General Public License,
same as upstream; see `LICENSE`.
