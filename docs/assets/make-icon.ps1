Add-Type -AssemblyName System.Drawing

$S = 32
$bmp = New-Object System.Drawing.Bitmap($S, $S, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode      = [System.Drawing.Drawing2D.SmoothingMode]::None
$g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighSpeed
$g.PixelOffsetMode    = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$g.Clear([System.Drawing.Color]::Transparent)

function C($r,$gr,$b) { [System.Drawing.Color]::FromArgb(255,$r,$gr,$b) }
function SB($col) { New-Object System.Drawing.SolidBrush($col) }

$navy  = C 15 30 52
$navy2 = C 32 58 88
$grid  = C 23 44 68

# --- background: filled navy square with a lighter 1px frame + faint blueprint grid ---
$g.FillRectangle((SB $navy), 0, 0, $S, $S)
$g.DrawRectangle((New-Object System.Drawing.Pen($navy2,1)), 0, 0, $S-1, $S-1)
$gp = New-Object System.Drawing.Pen($grid,1)
foreach ($x in 8,16,24) { $g.DrawLine($gp, $x, 1, $x, $S-2) }
foreach ($y in 8,16,24) { $g.DrawLine($gp, 1, $y, $S-2, $y) }

# --- hammer (rotated so head sits upper-right, handle lower-left) ---
$st = $g.Save()
$g.TranslateTransform(17,15); $g.RotateTransform(-34); $g.TranslateTransform(-16,-16)

$woodO = C 54 37 21;  $wood = C 122 80 45;  $woodH = C 168 120 68
$ironO = C 38 42 50;  $iron = C 150 157 168; $ironD = C 112 119 132; $ironH = C 214 220 230

# handle (vertical in rotated space) — long, reaches toward the corner
$g.FillRectangle((SB $woodO), 13, 12, 6, 22)
$g.FillRectangle((SB $wood),  14, 13, 4, 20)
$g.FillRectangle((SB $woodH), 14, 13, 1, 19)

# head (compact iron block seated on the handle top)
$g.FillRectangle((SB $ironO),  6, 4, 20, 11)
$g.FillRectangle((SB $iron),   7, 5, 18,  9)
$g.FillRectangle((SB $ironD), 17, 5,  8,  9)
$g.FillRectangle((SB $ironH),  7, 5, 18,  1)
$g.FillRectangle((SB $ironH),  7, 5,  1,  8)

$g.Restore($st)
$g.Dispose()

function Scale($src, $out, $size) {
  $o  = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $og = [System.Drawing.Graphics]::FromImage($o)
  $og.InterpolationMode  = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
  $og.PixelOffsetMode    = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
  $og.SmoothingMode      = [System.Drawing.Drawing2D.SmoothingMode]::None
  $og.DrawImage($src, 0, 0, $size, $size)
  $o.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
  $og.Dispose(); $o.Dispose()
}

$root = "D:\dev\proyects\mincraft-ux-litematica"
$scratch = "C:\Users\DGalo\AppData\Local\Temp\claude\D--dev-proyects-mincraft-ux-litematica\6e5f9329-3c23-47bf-9531-6bd60e57e812\scratchpad"
Scale $bmp "$scratch\icon-preview-512.png" 512
Scale $bmp "$scratch\icon-preview-128.png" 128
$bmp.Dispose()
Write-Output "done"
