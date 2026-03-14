$ErrorActionPreference = "Stop"

# Create new layout
New-Item -ItemType Directory -Force -Path "core\src\main\java\com\example\playerdatasync"
New-Item -ItemType Directory -Force -Path "core\src\main\resources"
New-Item -ItemType Directory -Force -Path "api\src\main\java\com\example\playerdatasync\nms"
New-Item -ItemType Directory -Force -Path "v1_8_R3\src\main\java\com\example\playerdatasync\nms\v1_8_R3"
New-Item -ItemType Directory -Force -Path "v1_12_R1\src\main\java\com\example\playerdatasync\nms\v1_12_R1"
New-Item -ItemType Directory -Force -Path "v1_14_R1\src\main\java\com\example\playerdatasync\nms\v1_14_R1"
New-Item -ItemType Directory -Force -Path "v1_16_R3\src\main\java\com\example\playerdatasync\nms\v1_16_R3"
New-Item -ItemType Directory -Force -Path "v1_17_R1\src\main\java\com\example\playerdatasync\nms\v1_17_R1"
New-Item -ItemType Directory -Force -Path "v1_19_R1\src\main\java\com\example\playerdatasync\nms\v1_19_R1"
New-Item -ItemType Directory -Force -Path "v1_21_R1\src\main\java\com\example\playerdatasync\nms\v1_21_R1"

# Move source code
$items = Get-ChildItem -Path "src\main\java\com\example\playerdatasync" -Exclude "nms"
foreach ($item in $items) {
    Move-Item -Path $item.FullName -Destination "core\src\main\java\com\example\playerdatasync\" -Force
}

# Move resources
Move-Item -Path "src\main\resources\*" -Destination "core\src\main\resources\" -Force

# Move NMSHandler
Move-Item -Path "src\main\java\com\example\playerdatasync\nms\NMSHandler.java" -Destination "api\src\main\java\com\example\playerdatasync\nms\" -Force

# We don't need the old src anymore
Remove-Item -Path "src" -Recurse -Force

Write-Host "Restructure complete."
