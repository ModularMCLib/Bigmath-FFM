param(
	[Parameter(Mandatory = $true)]
	[ValidateSet("windows-x86-64", "windows-aarch64")]
	[string]$Label,
	[Parameter(Mandatory = $true)]
	[ValidateSet("x64-windows", "arm64-windows")]
	[string]$Triplet
)

$ErrorActionPreference = "Stop"

$expectedTriplet = if ($Label -eq "windows-x86-64") { "x64-windows" } else { "arm64-windows" }
$archDirectory = if ($Label -eq "windows-x86-64") { "x64" } else { "arm64" }
if ($Triplet -ne $expectedTriplet) {
	throw "Label $Label requires vcpkg triplet $expectedTriplet"
}

$repoRoot = (Get-Location).Path
$nativeDir = Join-Path $repoRoot "native\$Label"
$licensesDir = Join-Path $nativeDir "licenses"
$sourceNative = Join-Path $repoRoot "build\native\lib"
$vcpkgBinDir = Join-Path $repoRoot "vcpkg_installed\$Triplet\bin"

New-Item -ItemType Directory -Force -Path $nativeDir, $licensesDir | Out-Null
foreach ($sourceDir in @($sourceNative, $vcpkgBinDir)) {
	if (-not (Test-Path -LiteralPath $sourceDir -PathType Container)) {
		throw "Required runtime directory not found: $sourceDir"
	}
	Get-ChildItem -LiteralPath $sourceDir -File -Filter "*.dll" | ForEach-Object {
		Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $nativeDir $_.Name) -Force
	}
}

foreach ($requiredPattern in @("bigmath_ffm.dll", "*gmp*.dll", "*mpfr*.dll")) {
	if (-not (Get-ChildItem -LiteralPath $nativeDir -File -Filter $requiredPattern | Select-Object -First 1)) {
		throw "Required Windows runtime is missing for $Label: $requiredPattern"
	}
}

# Preserve GNU/MinGW runtimes when GMP, MPFR, or another bundled DLL uses them.
foreach ($sourceDir in @(
	$sourceNative,
	$vcpkgBinDir,
	$(if ($env:MINGW_PREFIX) { Join-Path $env:MINGW_PREFIX "bin" }),
	$(if ($env:MSYSTEM_PREFIX) { Join-Path $env:MSYSTEM_PREFIX "bin" })
) | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Container) }) {
	foreach ($pattern in @("libstdc++-6.dll", "libgcc_s_*.dll", "libwinpthread-1.dll")) {
		Get-ChildItem -LiteralPath $sourceDir -File -Filter $pattern | ForEach-Object {
			Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $nativeDir $_.Name) -Force
		}
	}
}

function Find-ArchitectureRuntime {
	param([string[]]$Roots, [string]$Name, [string]$Architecture)
	foreach ($root in $Roots) {
		if (-not $root -or -not (Test-Path -LiteralPath $root)) {
			continue
		}
		$file = Get-ChildItem -LiteralPath $root -Recurse -File -Filter $Name -ErrorAction SilentlyContinue |
			Where-Object { $_.FullName.Split([IO.Path]::DirectorySeparatorChar) -contains $Architecture } |
			Sort-Object FullName |
			Select-Object -First 1
		if ($file) {
			return $file.FullName
		}
	}
	return $null
}

$redistRoots = @(
	$env:VCToolsRedistDir,
	$(if ($env:VCINSTALLDIR) { Join-Path $env:VCINSTALLDIR "Redist\MSVC" }),
	"C:\Program Files\Microsoft Visual Studio\2022\Enterprise\VC\Redist\MSVC",
	"C:\Program Files\Microsoft Visual Studio\2022\Professional\VC\Redist\MSVC",
	"C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Redist\MSVC",
	"C:\Program Files\Microsoft Visual Studio\2022\BuildTools\VC\Redist\MSVC"
)
foreach ($dllName in @("msvcp140.dll", "vcruntime140.dll", "vcruntime140_1.dll", "concrt140.dll")) {
	$dllPath = Find-ArchitectureRuntime -Roots $redistRoots -Name $dllName -Architecture $archDirectory
	if (-not $dllPath) {
		throw "Failed to locate the $archDirectory build of $dllName; cross-architecture fallback is forbidden"
	}
	Copy-Item -LiteralPath $dllPath -Destination (Join-Path $nativeDir $dllName) -Force
}

Copy-Item -LiteralPath (Join-Path $repoRoot "LICENSE") -Destination (Join-Path $licensesDir "LGPL-3.0.txt") -Force
Copy-Item -LiteralPath (Join-Path $repoRoot "THIRD_PARTY_NOTICES.md") -Destination (Join-Path $licensesDir "THIRD_PARTY_NOTICES.md") -Force
foreach ($component in @("gmp", "mpfr")) {
	$notice = Join-Path $repoRoot "vcpkg_installed\$Triplet\share\$component\copyright"
	if (-not (Test-Path -LiteralPath $notice -PathType Leaf)) {
		throw "Required $component notice not found: $notice"
	}
	Copy-Item -LiteralPath $notice -Destination (Join-Path $licensesDir "$component-copyright.txt") -Force
}

$utf8NoBom = [Text.UTF8Encoding]::new($false)
$msvcNotice = @"
Microsoft Visual C++ runtime files were staged from the architecture-matched
Visual Studio redistributable directory. Redistribution is governed by:
https://learn.microsoft.com/en-gb/visualstudio/releases/2022/redistribution
"@
[IO.File]::WriteAllText((Join-Path $licensesDir "MICROSOFT_VISUAL_CPP_REDIST_NOTICE.txt"), $msvcNotice, $utf8NoBom)

$gnuFiles = @(Get-ChildItem -LiteralPath $nativeDir -File | Where-Object {
	$_.Name -eq "libstdc++-6.dll" -or $_.Name -like "libgcc_s_*.dll" -or $_.Name -eq "libwinpthread-1.dll"
})
if ($gnuFiles.Count -gt 0) {
	$licenseRoots = @(
		(Join-Path $sourceNative "licenses"),
		(Join-Path $repoRoot "vcpkg_installed\$Triplet\share"),
		$(if ($env:MINGW_PREFIX) { Join-Path $env:MINGW_PREFIX "share\licenses" }),
		$(if ($env:MSYSTEM_PREFIX) { Join-Path $env:MSYSTEM_PREFIX "share\licenses" })
	) | Where-Object { $_ -and (Test-Path -LiteralPath $_) }
	$requiredNotices = @()
	if ($gnuFiles.Name -contains "libstdc++-6.dll" -or @($gnuFiles.Name | Where-Object { $_ -like "libgcc_s_*.dll" }).Count -gt 0) {
		$requiredNotices += @{ Name = "COPYING.RUNTIME"; Target = "COPYING.RUNTIME.txt" }
	}
	if ($gnuFiles.Name -contains "libwinpthread-1.dll") {
		$requiredNotices += @{ Name = "COPYING.winpthreads"; Target = "COPYING.winpthreads.txt" }
	}
	foreach ($required in $requiredNotices) {
		$notice = Get-ChildItem -LiteralPath $licenseRoots -Recurse -File -ErrorAction SilentlyContinue |
			Where-Object { $_.Name -eq $required.Name -or $_.Name -eq "$($required.Name).txt" } |
			Select-Object -First 1
		if (-not $notice) {
			throw "Bundled GNU/MinGW runtimes require $($required.Target)"
		}
		Copy-Item -LiteralPath $notice.FullName -Destination (Join-Path $licensesDir $required.Target) -Force
	}
}

Get-ChildItem -LiteralPath $nativeDir -Recurse
