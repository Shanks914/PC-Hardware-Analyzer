Add-Type -AssemblyName System.Drawing

$repoRoot = Split-Path $PSScriptRoot -Parent
$iconDirectory = Join-Path $repoRoot 'src/main/resources/icons'
New-Item -ItemType Directory -Force -Path $iconDirectory | Out-Null

function New-RoundedRectanglePath([single]$X, [single]$Y, [single]$Width, [single]$Height, [single]$Radius) {
    $path = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $diameter = $Radius * 2
    $path.AddArc($X, $Y, $diameter, $diameter, 180, 90)
    $path.AddArc($X + $Width - $diameter, $Y, $diameter, $diameter, 270, 90)
    $path.AddArc($X + $Width - $diameter, $Y + $Height - $diameter, $diameter, $diameter, 0, 90)
    $path.AddArc($X, $Y + $Height - $diameter, $diameter, $diameter, 90, 90)
    $path.CloseFigure()
    return $path
}

function New-AppIconBitmap([int]$Size) {
    $bitmap = [System.Drawing.Bitmap]::new($Size, $Size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graphics.ScaleTransform($Size / 256.0, $Size / 256.0)
    $graphics.Clear([System.Drawing.Color]::Transparent)

    $background = New-RoundedRectanglePath 8 8 240 240 52
    $backgroundBrush = [System.Drawing.Drawing2D.LinearGradientBrush]::new(
        [System.Drawing.Rectangle]::new(8, 8, 240, 240),
        [System.Drawing.Color]::FromArgb(23, 35, 51),
        [System.Drawing.Color]::FromArgb(11, 14, 20), 45)
    $graphics.FillPath($backgroundBrush, $background)
    $borderPen = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(41, 56, 73), 4)
    $graphics.DrawPath($borderPen, $background)

    $pinPen = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(105, 212, 155), 9)
    $pinPen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pinPen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    foreach ($position in @(88, 128, 168)) {
        $graphics.DrawLine($pinPen, $position, 51, $position, 69)
        $graphics.DrawLine($pinPen, $position, 187, $position, 205)
    }
    foreach ($position in @(88, 128, 168)) {
        $graphics.DrawLine($pinPen, 51, $position, 69, $position)
        $graphics.DrawLine($pinPen, 187, $position, 205, $position)
    }

    $chipPath = New-RoundedRectanglePath 69 69 118 118 22
    $chipBrush = [System.Drawing.Drawing2D.LinearGradientBrush]::new(
        [System.Drawing.Rectangle]::new(69, 69, 118, 118),
        [System.Drawing.Color]::FromArgb(120, 226, 170),
        [System.Drawing.Color]::FromArgb(54, 174, 122), 45)
    $graphics.FillPath($chipBrush, $chipPath)
    $innerPath = New-RoundedRectanglePath 83 83 90 90 15
    $innerBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(16, 25, 35))
    $graphics.FillPath($innerBrush, $innerPath)

    $barBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(105, 212, 155))
    $graphics.FillRectangle($barBrush, 99, 128, 14, 23)
    $graphics.FillRectangle($barBrush, 121, 113, 14, 38)
    $graphics.FillRectangle($barBrush, 143, 99, 14, 52)
    $trendPen = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(232, 255, 242), 5)
    $trendPen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $trendPen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $trendPen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
    $points = [System.Drawing.PointF[]]@(
        [System.Drawing.PointF]::new(98, 115),
        [System.Drawing.PointF]::new(122, 101),
        [System.Drawing.PointF]::new(143, 106),
        [System.Drawing.PointF]::new(158, 88))
    $graphics.DrawLines($trendPen, $points)
    $highlight = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(232, 255, 242))
    $graphics.FillEllipse($highlight, 153, 83, 10, 10)

    $highlight.Dispose(); $trendPen.Dispose(); $barBrush.Dispose(); $innerBrush.Dispose()
    $innerPath.Dispose(); $chipBrush.Dispose(); $chipPath.Dispose(); $pinPen.Dispose()
    $borderPen.Dispose(); $backgroundBrush.Dispose(); $background.Dispose(); $graphics.Dispose()
    return $bitmap
}

$pngIcon = New-AppIconBitmap 512
$pngIcon.Save((Join-Path $iconDirectory 'pc-hardware-analyzer.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$pngIcon.Dispose()

$sizes = @(16, 24, 32, 48, 64, 128, 256)
$images = foreach ($size in $sizes) {
    $bitmap = New-AppIconBitmap $size
    $memory = [System.IO.MemoryStream]::new()
    $bitmap.Save($memory, [System.Drawing.Imaging.ImageFormat]::Png)
    $data = $memory.ToArray()
    $memory.Dispose(); $bitmap.Dispose()
    [PSCustomObject]@{ Size = $size; Data = $data }
}

$icoPath = Join-Path $iconDirectory 'pc-hardware-analyzer.ico'
$fileStream = [System.IO.File]::Open($icoPath, [System.IO.FileMode]::Create)
$writer = [System.IO.BinaryWriter]::new($fileStream)
$writer.Write([UInt16]0)
$writer.Write([UInt16]1)
$writer.Write([UInt16]$images.Count)
$offset = 6 + (16 * $images.Count)
foreach ($image in $images) {
    $dimension = if ($image.Size -eq 256) { [byte]0 } else { [byte]$image.Size }
    $writer.Write($dimension)
    $writer.Write($dimension)
    $writer.Write([byte]0)
    $writer.Write([byte]0)
    $writer.Write([UInt16]1)
    $writer.Write([UInt16]32)
    $writer.Write([UInt32]$image.Data.Length)
    $writer.Write([UInt32]$offset)
    $offset += $image.Data.Length
}
foreach ($image in $images) { $writer.Write([byte[]]$image.Data) }
$writer.Dispose()
$fileStream.Dispose()

Write-Output "Created PNG and multi-size ICO under $iconDirectory"
