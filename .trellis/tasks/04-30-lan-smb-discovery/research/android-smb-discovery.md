# Research: Android LAN SMB Host Discovery

- Query: Research Android LAN SMB host discovery implementation for this project, focusing on NsdManager mDNS `_smb._tcp`, NetBIOS/NBT UDP 137 name query, TCP 445 reachability scan, permissions, testable abstractions, and connection form UX/state patterns.
- Scope: mixed
- Date: 2026-04-30

## Findings

### Files Found

- `app/src/main/AndroidManifest.xml` - current manifest already declares `INTERNET`, `ACCESS_NETWORK_STATE`, and transfer/storage permissions; it does not declare Wi-Fi multicast, Nearby Wi-Fi, or local network permissions.
- `app/build.gradle.kts` - Android app targets `compileSdk = 36`, `targetSdk = 36`, and `minSdk = 28`.
- `gradle/libs.versions.toml` - existing stack includes Kotlin 2.2.21, coroutines 1.10.2, lifecycle 2.9.4, MockK 1.13.11, Robolectric 4.12.2, and SMBJ 0.14.0.
- `app/src/main/java/com/qi/smbshare/data/model/SMBConfig.kt` - saved connection model already has `serverAddress`, `port` defaulting to 445, `shareName`, credentials, and anonymous flag.
- `app/src/main/java/com/qi/smbshare/data/local/SMBConnectionManager.kt` - current SMB connectivity uses SMBJ directly and tests a full authenticated share connection.
- `app/src/main/java/com/qi/smbshare/domain/usecase/ConnectSMBUseCase.kt` - use case wraps connection work in `Result<T>` and maps unexpected exceptions to `IOException`.
- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionState.kt` - connection UI state stores saved configs, current form config, loading flags, error/test result, and navigation effects.
- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionIntent.kt` - connection actions are modeled as a sealed intent plus `FormField` enum.
- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionViewModel.kt` - ViewModel owns business work, exposes `StateFlow`, performs blocking network work on `Dispatchers.IO`, and clears one-shot UI state via intents.
- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionScreen.kt` - saved connection list uses `collectAsStateWithLifecycle()`, Snackbar one-shot effects, empty state, theme colors, and a FAB for adding connections.
- `app/src/main/java/com/qi/smbshare/ui/connection/EditConnectionScreen.kt` - connection form already supports loading indicators on test/save/connect buttons and validates with simple `canConnect`/`canSave` helpers.
- `app/src/main/java/com/qi/smbshare/util/ErrorHandler.kt` - central mapper classifies network, auth, permission, file, and unknown errors into localized messages.
- `app/src/main/java/com/qi/smbshare/util/PermissionManager.kt` - existing permission pattern tracks first request vs permanent denial and exposes `PermissionStatus`.
- `app/src/test/java/com/qi/smbshare/domain/usecase/ConnectSMBUseCaseTest.kt` - local unit tests use Robolectric, MockK, and `runTest` to verify use case `Result<T>` behavior.

### Code Patterns

- Keep discovery outside Composables. Project structure says `data/` owns SMB/local capabilities, `domain/usecase/` owns action wrappers, and `ui/<feature>/` owns screen/ViewModel/state/intent patterns (`.trellis/spec/backend/directory-structure.md:14`).
- State shape should follow existing `ConnectionState`: immutable `data class`, booleans for concurrent operations, nullable one-shot fields for `error`/result/navigation, and derived form getters (`app/src/main/java/com/qi/smbshare/ui/connection/ConnectionState.kt:5`).
- Add discovery actions to `ConnectionIntent`, not direct UI callbacks. Existing intents cover load/save/delete/connect/test/form update/navigation (`app/src/main/java/com/qi/smbshare/ui/connection/ConnectionIntent.kt:5`).
- `ConnectionViewModel` exposes private `MutableStateFlow` as public `StateFlow` (`app/src/main/java/com/qi/smbshare/ui/connection/ConnectionViewModel.kt:34`) and dispatches work from `handleIntent` (`app/src/main/java/com/qi/smbshare/ui/connection/ConnectionViewModel.kt:41`).
- Blocking network work belongs on IO dispatcher. Existing connect/test methods call SMB work inside `withContext(Dispatchers.IO)` (`app/src/main/java/com/qi/smbshare/ui/connection/ConnectionViewModel.kt:158`, `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionViewModel.kt:198`).
- Existing connection test logs server, port, share, anonymous flag, and masks password (`app/src/main/java/com/qi/smbshare/data/local/SMBConnectionManager.kt:120`); discovery logs must not add credentials or full serialized configs.
- Use case style returns `Result<T>` and converts unexpected exceptions into `IOException` (`app/src/main/java/com/qi/smbshare/domain/usecase/ConnectSMBUseCase.kt:10`).
- UI collects state through lifecycle-aware collection (`app/src/main/java/com/qi/smbshare/ui/connection/ConnectionScreen.kt:62`, `app/src/main/java/com/qi/smbshare/ui/connection/EditConnectionScreen.kt:31`).
- One-shot success/error UX is Snackbar plus clear intent (`app/src/main/java/com/qi/smbshare/ui/connection/ConnectionScreen.kt:101`, `app/src/main/java/com/qi/smbshare/ui/connection/EditConnectionScreen.kt:38`).
- Form actions already disable buttons while work is active and show inline `CircularProgressIndicator` (`app/src/main/java/com/qi/smbshare/ui/connection/EditConnectionScreen.kt:224`).
- Theme rule: use `MaterialTheme.colorScheme` / typography and string resources for new UI text; avoid component-level `Color(0x...)` (`.trellis/spec/backend/quality-guidelines.md:35`).

### Recommended Architecture

- Add data models in `data/model/`, for example:
  - `SmbDiscoveryHost(id, displayName, hostAddress, port, source, isReachable, lastSeenAt, aliases, workgroup)`
  - `SmbDiscoverySource { MDNS, NETBIOS, TCP_445_SCAN }`
  - `SmbDiscoveryProgress(scannedHosts, totalHosts, activeSources)`
- Add discovery implementation under `data/local/` or `data/repository/`:
  - `SmbDiscoveryRepository` orchestrates sources and exposes `Flow<SmbDiscoveryEvent>` or `Flow<List<SmbDiscoveryHost>>`.
  - `AndroidNsdSmbDiscoverySource` wraps `NsdManager`.
  - `NetBiosNameServiceClient` handles UDP 137 query/response encoding.
  - `TcpPortReachabilityScanner` performs bounded-concurrency TCP 445 checks.
  - `NetworkEnvironment` resolves active Wi-Fi/Ethernet network, local address, prefix length, broadcast addresses, and whether LAN scan is available.
- Add domain use cases:
  - `DiscoverSmbHostsUseCase` starts discovery and returns a cold `Flow` so collection cancellation stops discovery.
  - Optional `ResolveDiscoveredSmbHostUseCase` maps a host candidate into partial form fields.
- Extend `ConnectionViewModel`:
  - State: `isDiscovering`, `discoveredHosts`, `discoveryError`, `selectedDiscoveredHost`, maybe `discoveryProgress`.
  - Intents: `StartDiscovery`, `StopDiscovery`, `SelectDiscoveredHost(host)`, `ClearDiscoveryError`.
  - Selecting a host should update `SERVER_ADDRESS` and `PORT`; leave `SHARE_NAME` empty unless mDNS TXT/path provides a safe suggestion, because browsing shares requires authentication and is separate from host discovery.

### mDNS via NsdManager

- Use `NsdManager.discoverServices("_smb._tcp", NsdManager.PROTOCOL_DNS_SD, listener)` for API 16+ compatibility, or executor/network overloads where useful on newer APIs.
- Android `NsdManager` is asynchronous and currently supports DNS-SD over local mDNS only; callbacks are delivered on listener callbacks, not synchronously.
- Discovery continues until `stopServiceDiscovery(listener)` is called; call it when user stops scanning, leaves screen, ViewModel clears, or collection is cancelled.
- Prefer `_smb._tcp` because IANA lists service name `smb` as "Server Message Block over TCP/IP" and TXT keys `u`, `p`, `path`; DNS-SD service type composition is underscore service + `_tcp`.
- On API 34+, `resolveService` is deprecated because resolved info can be stale. If using API 35+ paths, prefer `registerServiceInfoCallback(DiscoveryRequest, Executor, ServiceInfoCallback)` for live updates; keep an API 16 fallback if needed because project `minSdk = 28`.
- Normalize mDNS results by host address + port, merge with TCP/NetBIOS candidates, and prefer reachable 445 status over advertising alone.

### NetBIOS/NBT UDP 137

- NBT name service uses port 137. RFC 1002 defines detailed NetBIOS-over-TCP packet formats and constants; IANA registers `netbios-ns` on TCP/UDP 137.
- For host discovery/name enrichment, implement UDP name-service queries on `Dispatchers.IO` with short socket timeouts. Useful probes:
  - Directed-broadcast or subnet-candidate NBNS name query for SMB server service names where applicable.
  - Node Status (`NBSTAT`, question type `0x0021`) to a wildcard NetBIOS name can return a node name table and is often more useful for extracting host/workgroup aliases than a plain specific-name query.
- Encode NetBIOS names according to RFC 1002 first/second-level encoding rules: 16-byte padded name plus suffix encoded into the DNS-like label representation.
- Parse responses defensively: transaction id match, response flag, answer count, resource type/class, TTL, and variable RDATA length before reading names. Treat malformed or partial datagrams as ignored candidates, not fatal discovery failure.
- UDP broadcast is often filtered by routers, Android device policy, VPNs, or AP isolation. NetBIOS is legacy; do not rely on it as the only discovery source.
- If a UDP 137 response provides an IP/name but TCP 445 is closed, show it as "found but unavailable" only if UX needs it; otherwise filter to reachable hosts.

### TCP 445 Reachability Scan

- Scan TCP 445 because current `SMBConfig.port` defaults to 445 and SMBJ connects to `serverAddress:port`.
- Derive candidate IPv4 addresses from active LAN prefix when possible; cap scope to local subnet and avoid cellular/VPN interfaces. For large prefixes, cap host count or require explicit user action to avoid slow/battery-heavy scans.
- Use `Socket.connect(InetSocketAddress(ip, 445), timeoutMs)` with a low timeout, bounded concurrency (for example 16-32 workers), and cancellation checks. A successful connect means "SMB port reachable", not "credentials/share valid".
- Merge scan hits with mDNS/NetBIOS hits by IP. Enrich display names from mDNS instance names and NetBIOS names; keep IP as the stable address for form filling.
- Do not automatically run a full SMB auth/share test for every discovered host. Keep full validation behind existing "测试连接" because that requires share name and credentials.

### Permissions

- Already present:
  - `INTERNET` allows network sockets and is required for SMBJ sockets, TCP 445 scan, and UDP 137 sockets (`app/src/main/AndroidManifest.xml:4`).
  - `ACCESS_NETWORK_STATE` allows network information and is already present (`app/src/main/AndroidManifest.xml:17`); NsdManager network-request overloads also require it.
- Add for multicast support:
  - `CHANGE_WIFI_MULTICAST_STATE` is a normal permission that allows Wi-Fi multicast mode; needed if implementation uses `WifiManager.MulticastLock`.
  - Hold `MulticastLock` only while active mDNS/UDP discovery is running. Android docs warn multicast packets can increase battery drain, so release promptly.
- Android 13+ nearby Wi-Fi:
  - If using Wi-Fi APIs that require Nearby Wi-Fi, declare `NEARBY_WIFI_DEVICES` with `android:usesPermissionFlags="neverForLocation"` when the app does not derive physical location.
  - Android 16 local network restriction is opt-in for target SDK 36; under restriction, direct local sockets fail unless Nearby Wi-Fi access restores local network access. This affects UDP 137 and TCP 445 socket scan more than framework-run `NsdManager`.
- Android 17+ future:
  - Android docs show `ACCESS_LOCAL_NETWORK` added in API 37 and dangerous; it is required to advertise/connect to local network devices in Android 17 enforcement for target SDK 37+.
  - Current project compiles against SDK 36, so do not reference `Manifest.permission.ACCESS_LOCAL_NETWORK` in source until compile SDK is raised or guard via manifest string/resource strategy in a future target upgrade.
- No location permission should be needed for this feature if the app does not scan Wi-Fi SSIDs/BSSIDs. Do not add `ACCESS_FINE_LOCATION` solely for SMB host discovery.

### Testable Abstractions

- Wrap Android/platform classes behind interfaces so JVM tests can run without real LAN:
  - `NsdClient`: start/stop service discovery and emit raw service info objects or domain DTOs.
  - `UdpSocketClient`: send datagram, receive datagrams with timeout.
  - `TcpSocketChecker`: `suspend fun canConnect(host: InetAddress, port: Int, timeoutMs: Int): Boolean`.
  - `LocalNetworkProvider`: active LAN addresses, prefix length, broadcast addresses, and whether scanning is permitted.
  - `DiscoveryClock` or injectable time provider for `lastSeenAt`.
  - `CoroutineDispatcher` injection for IO work.
- Keep packet encoder/parser pure Kotlin:
  - Unit-test NetBIOS name encoding for padding/suffix.
  - Unit-test NBNS/NBSTAT response parsing with byte arrays.
  - Unit-test candidate merge/dedup/source-priority logic with deterministic DTOs.
- Use existing test stack:
  - MockK + `runTest` for use cases and repository orchestration, matching `ConnectSMBUseCaseTest`.
  - Robolectric for manifest/permission helper behavior if `PermissionManager` is extended.
  - Avoid real sockets in unit tests; add a small fake `UdpSocketClient` and `TcpSocketChecker`.
- Cancellation tests should verify that stopping discovery calls `NsdClient.stop`, releases multicast lock, and cancels scan jobs.

### UX / Connection Form State

- Entry point: add a discovery action near the server address field in `EditConnectionScreen` or as a compact section above fields. Keep the first screen useful and avoid a separate landing/explanation page.
- Basic states:
  - Idle: "扫描局域网" action enabled.
  - Discovering: progress indicator and "停止" action; disable duplicate starts.
  - Empty: localized text such as "未发现可用的 SMB 主机".
  - Error/permission denied: Snackbar plus optional permission rationale dialog following current `PermissionManager` style.
  - Results: list rows showing display name, IP, source chips (mDNS/NetBIOS/445), and reachability.
- Selecting a host should fill server address and port 445, then focus/share-name guidance can remain implicit through existing required field validation. Do not auto-save until user taps save/connect.
- Preserve current behavior: manual entry must still work if discovery is unavailable, denied, cancelled, or empty.
- All UI strings should be added to `values/strings.xml` and `values-zh/strings.xml`; Kotlin comments/logs should be Simplified Chinese.

### External References

- Android `NsdManager` API reference: https://developer.android.com/reference/android/net/nsd/NsdManager
  - DNS-SD over local mDNS, async listener callbacks, discovery/resolve operations, stop discovery requirement, `resolveService` deprecation on API 34.
- Android Local network permission: https://developer.android.com/privacy-and-security/local-network-permission
  - Android 16 opt-in local network restrictions, Android 17 enforcement, interaction with `NEARBY_WIFI_DEVICES` and future `ACCESS_LOCAL_NETWORK`.
- Android Nearby Wi-Fi devices permission: https://developer.android.com/develop/connectivity/wifi/wifi-permissions
  - `NEARBY_WIFI_DEVICES`, `neverForLocation`, runtime permission behavior.
- Android `Manifest.permission`: https://developer.android.com/reference/android/Manifest.permission
  - `INTERNET`, `ACCESS_NETWORK_STATE`, `CHANGE_WIFI_MULTICAST_STATE`, and `ACCESS_LOCAL_NETWORK` definitions.
- Android `WifiManager.MulticastLock`: https://developer.android.com/reference/android/net/wifi/WifiManager.MulticastLock
  - Multicast reception, acquire/release lifecycle, and battery warning.
- IANA service/port registry, search `smb`: https://www.iana.org/assignments/service-names-port-numbers/service-names-port-numbers.xhtml?search=smb
  - `smb` service name for "Server Message Block over TCP/IP" and TXT key notes.
- IANA service/port registry, search `137`: https://www.iana.org/assignments/service-names-port-numbers/service-names-port-numbers.xhtml?search=137
  - `netbios-ns` TCP/UDP 137.
- IANA service/port registry, search `Microsoft`: https://www.iana.org/assignments/service-names-port-numbers/service-names-port-numbers.xhtml?search=Microsoft
  - `microsoft-ds` TCP/UDP 445.
- RFC 1002, NetBIOS over TCP/UDP detailed specifications: https://datatracker.ietf.org/doc/html/rfc1002
  - NetBIOS name packet formats, UDP 137 constants, session service TCP 139 constants.
- RFC 6763, DNS-Based Service Discovery: https://www.rfc-editor.org/rfc/rfc6763.html
  - Service type naming convention: `_service._tcp` / `_service._udp`.

### Related Specs

- `.trellis/spec/backend/index.md` - Android app backend/spec entry point and relevant guideline index.
- `.trellis/spec/backend/directory-structure.md` - data/domain/ui placement, StateFlow/Intent pattern, and no blocking work in Composables.
- `.trellis/spec/backend/error-handling.md` - `Result<T>` use case, `ErrorHandler`, Snackbar error patterns.
- `.trellis/spec/backend/logging-guidelines.md` - Android `Log`, Chinese messages, no credentials/full config logs.
- `.trellis/spec/backend/quality-guidelines.md` - Compose state, theme colors, string resources, and unit test stack.

## Caveats / Not Found

- No existing LAN discovery implementation was found; this is a new capability.
- `prd.md` is not present in the task directory yet, so UX details such as exact placement, labels, and whether to show unreachable NetBIOS hosts need confirmation before implementation.
- mDNS `_smb._tcp` coverage depends on SMB servers advertising via DNS-SD; many Windows/NAS setups may only answer NetBIOS or only expose TCP 445.
- UDP broadcast and TCP subnet scanning may be blocked by AP isolation, VPN, firewall, Android local network restrictions, or battery/network policies.
- Android 17 `ACCESS_LOCAL_NETWORK` is API 37; current `compileSdk = 36` means implementation should document/prepare for it but not directly reference the constant until SDK upgrade.
- TCP 445 reachability proves only that the port accepted a connection. It does not prove share existence, auth success, SMB dialect compatibility, or file permission.
