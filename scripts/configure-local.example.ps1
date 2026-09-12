# Copy this file to scripts/configure-local.ps1, fill in your own values,
# then dot-source it from the repository root: . .\scripts\configure-local.ps1
# configure-local.ps1 is ignored by Git. Spring Boot reads these process variables.
$env:MALL_DB_PASSWORD = 'YOUR_MYSQL_PASSWORD'
$env:QQ_MAIL_USERNAME = 'YOUR_QQ_NUMBER@qq.com'
$env:QQ_MAIL_AUTH_CODE = 'YOUR_QQ_SMTP_AUTHORIZATION_CODE'

# Optional: a paid AI provider key. Local mall Q&A works without it.
# $env:CIYUANSHEN_TEXT_API_KEY = 'YOUR_TEXT_API_KEY'
