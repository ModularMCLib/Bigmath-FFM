param(
	[Parameter(Mandatory = $true)]
	[string]$Label,
	[string]$Triplet = ""
)

$ErrorActionPreference = "Stop"

$repoRoot = (Get-Location).Path
$nativeDir = Join-Path $repoRoot "native\$Label"
$licensesDir = Join-Path $nativeDir "licenses"

New-Item -ItemType Directory -Force -Path $nativeDir, $licensesDir | Out-Null

if (Test-Path (Join-Path $repoRoot "build\native\lib")) {
	Copy-Item -Path (Join-Path $repoRoot "build\native\lib\*") -Destination $nativeDir -Force
}

Copy-Item -Path (Join-Path $repoRoot "LICENSE") -Destination (Join-Path $licensesDir "LGPL-3.0.txt") -Force
Copy-Item -Path (Join-Path $repoRoot "THIRD_PARTY_NOTICES.md") -Destination (Join-Path $licensesDir "THIRD_PARTY_NOTICES.md") -Force

function Find-FirstFile {
	param(
		[string[]]$Roots,
		[string[]]$Names,
		[string]$ArchHint = ""
	)

	foreach ($root in $Roots) {
		if ([string]::IsNullOrWhiteSpace($root) -or -not (Test-Path $root)) {
			continue
		}
		$candidate = Get-ChildItem -Path $root -Recurse -File -ErrorAction SilentlyContinue |
			Where-Object {
				$Names -contains $_.Name -and
				([string]::IsNullOrWhiteSpace($ArchHint) -or $_.FullName.ToLowerInvariant().Contains($ArchHint.ToLowerInvariant()))
			} |
			Select-Object -First 1
		if ($candidate) {
			return $candidate.FullName
		}
	}
	return $null
}

if ($Label -eq "windows-x86-64") {
	$compilerDir = if ($env:CXX) { Split-Path -Parent $env:CXX } else { $null }
	$licenseRoots = @()
	if ($compilerDir) {
		$licenseRoots += Join-Path $compilerDir "..\share\licenses"
		$licenseRoots += Join-Path $compilerDir "..\..\share\licenses"
	}
	$licenseRoots += @(
		"C:\mingw64\share\licenses",
		"C:\msys64\mingw64\share\licenses",
		"C:\Program Files\Git\mingw64\share\licenses"
	)

	$gccRuntimeNotice = Find-FirstFile -Roots $licenseRoots -Names @("COPYING.RUNTIME")
	if ($gccRuntimeNotice) {
		Copy-Item -Path $gccRuntimeNotice -Destination (Join-Path $licensesDir "COPYING.RUNTIME.txt") -Force
	}

	$winpthreadNotice = Get-ChildItem -Path $licenseRoots -Recurse -File -ErrorAction SilentlyContinue |
		Where-Object {
			$_.FullName -match "winpthread|winpthreads" -and
			($_.Name -eq "COPYING.winpthreads" -or $_.Name -eq "COPYING")
		} |
		Select-Object -First 1
	if ($winpthreadNotice) {
		Copy-Item -Path $winpthreadNotice.FullName -Destination (Join-Path $licensesDir "COPYING.winpthreads.txt") -Force
	}
}

if ($Label -eq "windows-aarch64") {
	$redistRoots = @()
	if ($env:VCToolsRedistDir) {
		$redistRoots += $env:VCToolsRedistDir
	}
	if ($env:VCINSTALLDIR) {
		$redistRoots += Join-Path $env:VCINSTALLDIR "Redist\MSVC"
	}
	$redistRoots += @(
		"C:\Program Files\Microsoft Visual Studio\2022\Enterprise\VC\Redist\MSVC",
		"C:\Program Files\Microsoft Visual Studio\2022\Professional\VC\Redist\MSVC",
		"C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Redist\MSVC",
		"C:\Program Files (x86)\Microsoft Visual Studio\Installer\Feedback\arm64"
	)

	foreach ($dllName in @("msvcp140.dll", "vcruntime140.dll", "vcruntime140_1.dll", "concrt140.dll")) {
		$dllPath = Find-FirstFile -Roots $redistRoots -Names @($dllName) -ArchHint "arm64"
		if (-not $dllPath) {
			$dllPath = Find-FirstFile -Roots $redistRoots -Names @($dllName)
		}
		if ($dllPath) {
			Copy-Item -Path $dllPath -Destination (Join-Path $nativeDir $dllName) -Force
		} else {
			Write-Warning "Failed to locate $dllName for $Label"
		}
	}

	@"
Microsoft Visual C++ runtime files in this directory were staged from a
Visual Studio 2022 redistributable location on the build machine.

Redistribution is governed by the Visual Studio 2022 license terms and the
official distributable code list:
https://learn.microsoft.com/en-gb/visualstudio/releases/2022/redistribution
"@ | Set-Content -Path (Join-Path $licensesDir "MICROSOFT_VISUAL_CPP_REDIST_NOTICE.txt")
}

Get-ChildItem -Recurse $nativeDir
