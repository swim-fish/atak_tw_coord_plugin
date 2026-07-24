param(
    [Parameter(Mandatory = $true)]
    [string]$SourceXml,

    [Parameter(Mandatory = $true)]
    [string]$OutputJson,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^\d{4}-\d{2}-\d{2}$')]
    [string]$RetrievedOn
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$countyOrder = @(
    '基隆市',
    '臺北市',
    '新北市',
    '桃園市',
    '新竹市',
    '新竹縣',
    '苗栗縣',
    '臺中市',
    '彰化縣',
    '南投縣',
    '雲林縣',
    '嘉義市',
    '嘉義縣',
    '臺南市',
    '高雄市',
    '屏東縣',
    '臺東縣',
    '花蓮縣',
    '宜蘭縣',
    '澎湖縣',
    '金門縣',
    '連江縣'
)

$sourcePath = (Resolve-Path -LiteralPath $SourceXml).Path
[xml]$document = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$sourceHash = (Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash.ToLowerInvariant()
$generated = [string]$document.dataroot.generated

$localities = [System.Collections.Generic.List[object]]::new()
$sourceOrder = 0

foreach ($node in $document.dataroot.ChildNodes) {
    if ($node.NodeType -ne [System.Xml.XmlNodeType]::Element) {
        continue
    }

    $sourceOrder++
    $fullName = [string]$node.'行政區名'
    $postalPrefix = [string]$node.'_x0033_碼郵遞區號'
    $longitude = [double]::Parse(
        [string]$node.'中心點經度',
        [System.Globalization.CultureInfo]::InvariantCulture
    )
    $latitude = [double]::Parse(
        [string]$node.'中心點緯度',
        [System.Globalization.CultureInfo]::InvariantCulture
    )

    if ($postalPrefix -notmatch '^\d{3}$') {
        throw "Invalid postal prefix '$postalPrefix' for '$fullName'."
    }

    $county = $countyOrder |
        Where-Object { $fullName.StartsWith($_, [System.StringComparison]::Ordinal) } |
        Select-Object -First 1
    if ($null -eq $county) {
        throw "Unable to resolve county for '$fullName'."
    }

    $district = $fullName.Substring($county.Length)
    if ([string]::IsNullOrWhiteSpace($district)) {
        throw "Missing district for '$fullName'."
    }

    $localities.Add([ordered]@{
        county = $county
        district = $district
        postalPrefix = $postalPrefix
        sourceOrder = $sourceOrder
        center = [ordered]@{
            latitude = $latitude
            longitude = $longitude
        }
    })
}

$duplicateNames = @(
    $localities |
        Group-Object { "$($_.county)$($_.district)" } |
        Where-Object Count -gt 1
)
if ($duplicateNames.Count -gt 0) {
    throw "Duplicate county/district entries: $($duplicateNames.Name -join ', ')."
}

$counties = [System.Collections.Generic.List[object]]::new()
for ($countyIndex = 0; $countyIndex -lt $countyOrder.Count; $countyIndex++) {
    $countyName = $countyOrder[$countyIndex]
    $districtRows = @(
        $localities |
            Where-Object county -eq $countyName |
            Sort-Object @{ Expression = { [int]$_.postalPrefix } }, sourceOrder
    )
    if ($districtRows.Count -eq 0) {
        throw "No postal localities found for '$countyName'."
    }

    $districts = [System.Collections.Generic.List[object]]::new()
    for ($districtIndex = 0; $districtIndex -lt $districtRows.Count; $districtIndex++) {
        $row = $districtRows[$districtIndex]
        $districts.Add([ordered]@{
            name = $row.district
            postalPrefix = $row.postalPrefix
            postalOrder = $districtIndex + 1
            center = $row.center
        })
    }

    $counties.Add([ordered]@{
        name = $countyName
        selectorOrder = $countyIndex + 1
        districts = $districts
    })
}

$dataset = [ordered]@{
    schemaVersion = 1
    datasetId = 'chunghwa-post-taiwan-postal-localities'
    purpose = 'Order native Taiwan address county and district selectors; not for postal delivery validation.'
    retrievedOn = $RetrievedOn
    sources = [ordered]@{
        countySelectorOrder = [ordered]@{
            authority = 'Chunghwa Post Co., Ltd.'
            title = 'Postal Code Search'
            url = 'https://www.post.gov.tw/post/internet/SearchZone/index.jsp?ID=208'
            pageLastUpdated = '2026-06-30'
            orderBasis = 'County/city selector order shown by the official postal-code search page.'
        }
        postalPrefixes = [ordered]@{
            authority = 'Chunghwa Post Co., Ltd.'
            title = 'Taiwan 3-digit Postal Code List'
            sourceVersion = '103.12.25'
            effectiveDate = '2014-12-25'
            downloadItemUpdated = '2015-01-22'
            url = 'https://www.post.gov.tw/post/download/103.12.25-%E8%87%BA%E7%81%A3%E5%9C%B0%E5%8D%80%E9%83%B5%E9%81%9E%E5%8D%80%E8%99%9F%E5%89%8D3%E7%A2%BC%E4%B8%80%E8%A6%BD%E8%A1%A8.txt'
            sha256 = '7fe1eda82820cb0cbc2fe684ebeb5a817f08a7880e0208fbdb4ae95e9e8a69cf'
        }
        localityCenters = [ordered]@{
            authority = 'Chunghwa Post Co., Ltd.'
            title = '3-digit Postal Codes and Administrative-area Center Coordinates'
            sourceGeneratedAt = $generated
            downloadItemUpdated = '2023-04-14'
            url = 'https://www.post.gov.tw/post/download/1050812_%E8%A1%8C%E6%94%BF%E5%8D%80%E7%B6%93%E7%B7%AF%E5%BA%A6%28toPost%29.xml'
            sha256 = $sourceHash
        }
        terms = [ordered]@{
            url = 'https://subservices.post.gov.tw/post/internet/Download/index.jsp?ID=220306'
            note = 'See the official download page and published usage terms before redistributing refreshed source material.'
        }
    }
    countyCount = $counties.Count
    districtCount = $localities.Count
    counties = $counties
}

$outputPath = [System.IO.Path]::GetFullPath($OutputJson)
$outputDirectory = [System.IO.Path]::GetDirectoryName($outputPath)
[System.IO.Directory]::CreateDirectory($outputDirectory) | Out-Null
$json = $dataset | ConvertTo-Json -Depth 10
[System.IO.File]::WriteAllText(
    $outputPath,
    $json + [Environment]::NewLine,
    [System.Text.UTF8Encoding]::new($false)
)
