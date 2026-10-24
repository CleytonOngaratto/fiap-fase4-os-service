resource "random_password" "internal_service_token" {
  length  = 48
  special = false
}

# O valor so e lido em runtime: pela app (variavel de ambiente vinda do Secret) e pela Lambda de auth
# (GetParameters com decrypt). O endpoint interno recusa qualquer chamada sem ele.
resource "aws_ssm_parameter" "internal_service_token" {
  name  = "/fase4/${local.service}/internal/service-token"
  type  = "SecureString"
  value = random_password.internal_service_token.result
}
