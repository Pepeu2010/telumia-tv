# Atualizações estáveis no Android TV

O app consulta a lista de releases do repositório principal `Pepeu2010/telumia` ao iniciar e quando a pessoa seleciona **Verificar atualizações**. O canal é estável; releases alpha, beta, RC e rascunhos são sempre ignorados e não há seletor de versões de teste. A tela de configurações mostra a versão estável mais recente encontrada que tenha um APK compatível com a arquitetura do aparelho, mesmo quando essa versão já está instalada. Desativar o banner desativa o aviso automático, não a consulta nem a informação de versão.

No canal estável, a seleção ignora rascunhos e pré-lançamentos, ordena as versões por semver e procura da mais nova para a mais antiga um APK compatível. Isso evita depender do marcador `latest` do GitHub e permite usar o release mais recente que o dispositivo consegue instalar.

Em uma falha de rede, a tela conserva a última versão obtida durante a sessão e um update já conhecido continua disponível para download. Uma consulta bem-sucedida em uma próxima abertura ou ao tocar no botão atualiza essa informação.

## Incidente do login por QR

A release estável principal v1.0.11 contém o commit TV `4afb3243e83e42fd8423f2398a256da0ee523cdc`. O build dessa release lia apenas `NUVIO_SUPABASE_URL` e `NUVIO_SUPABASE_ANON_KEY`, mas o workflow usado na publicação não tinha uma etapa que injetasse a configuração de produção. Sem esses valores, a tela interrompia o login antes de pedir uma sessão ao backend.

O código atualizado aceita também `SUPABASE_URL` e `SUPABASE_ANON_KEY`, mostra um erro explícito quando falta configuração e o pipeline principal atual injeta e valida os segredos de produção antes de compilar. A correção chega ao canal estável quando o próximo APK de produção for publicado pelo workflow principal do repositório `Pepeu2010/telumia`.

O build local deste ambiente não possui `local.properties` nem credenciais de release. APKs Full Debug servem para validar compilação e interface; não substituem o pacote estável assinado pelo workflow.
