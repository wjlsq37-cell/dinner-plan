param(
    [string]$Message = "",
    [string]$RemoteUrl = ""
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $projectRoot

function Write-Step($message) {
    Write-Host ""
    Write-Host "==> $message" -ForegroundColor Cyan
}

function Require-Git {
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
        throw "Git is not installed or not available in PATH."
    }
}

function Ensure-GitUser {
    $name = git config user.name
    $email = git config user.email
    if ([string]::IsNullOrWhiteSpace($name)) {
        $name = Read-Host "Git user.name is empty. Enter your GitHub username"
        git config user.name $name
    }
    if ([string]::IsNullOrWhiteSpace($email)) {
        $email = Read-Host "Git user.email is empty. Enter your GitHub email"
        git config user.email $email
    }
}

function Ensure-Origin {
    $origin = git remote get-url origin 2>$null
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($origin)) {
        if ([string]::IsNullOrWhiteSpace($RemoteUrl)) {
            $RemoteUrl = Read-Host "Enter GitHub repository URL, for example https://github.com/user/repo.git"
        }
        if ([string]::IsNullOrWhiteSpace($RemoteUrl)) {
            throw "GitHub repository URL is required when origin is not configured."
        }
        git remote add origin $RemoteUrl
        $origin = $RemoteUrl
    }
    Write-Host "Remote origin: $origin"
}

function Ensure-Branch {
    $branch = git branch --show-current
    if ([string]::IsNullOrWhiteSpace($branch)) {
        $branch = "main"
        git checkout -B $branch
    }
    return $branch
}

Require-Git

if (-not (Test-Path ".git")) {
    Write-Step "Initialize Git repository"
    git init
}

Ensure-GitUser
Ensure-Origin
$branch = Ensure-Branch

if ([string]::IsNullOrWhiteSpace($Message)) {
    $Message = Read-Host "Commit message (press Enter for default)"
    if ([string]::IsNullOrWhiteSpace($Message)) {
        $Message = "Update project"
    }
}

Write-Step "Stage project files"
git add -A

$staged = git diff --cached --name-only
if (-not [string]::IsNullOrWhiteSpace($staged)) {
    Write-Step "Commit changes"
    git commit -m $Message
} else {
    Write-Step "No file changes to commit"
}

Write-Step "Push to GitHub"
$upstream = git rev-parse --abbrev-ref --symbolic-full-name "@{u}" 2>$null
if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($upstream)) {
    git push
} else {
    git push -u origin $branch
}

Write-Host ""
Write-Host "Push finished." -ForegroundColor Green
