param(
    [Parameter(Mandatory=$true)]
    [string]$Path
)
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($Path)
try {
    $entry = $zip.Entries | Where-Object { $_.FullName -eq 'word/document.xml' }
    if (-not $entry) {
        Write-Error "document.xml not found in docx"
        exit 1
    }
    $sr = New-Object System.IO.StreamReader($entry.Open())
    $xml = [xml]$sr.ReadToEnd()
    $ns = New-Object System.Xml.XmlNamespaceManager($xml.NameTable)
    $ns.AddNamespace('w','http://schemas.openxmlformats.org/wordprocessingml/2006/main')
    $paras = $xml.SelectNodes('//w:body/w:p',$ns)
    foreach ($p in $paras) {
        $texts = $p.SelectNodes('.//w:t',$ns)
        $line = ($texts | ForEach-Object { $_.InnerText }) -join ''
        Write-Output $line
    }
} finally {
    $zip.Dispose()
}
