# Atualizações estáveis no Android TV

O app consulta a lista de releases do repositório principal `Pepeu2010/telumia` ao iniciar e quando a pessoa seleciona **Verificar atualizações**. O canal é estável; releases alpha, beta, RC e rascunhos são sempre ignorados e não há seletor de versões de teste. A tela de configurações mostra a versão estável mais recente encontrada que tenha um APK compatível com a arquitetura do aparelho, mesmo quando essa versão já está instalada. Desativar o banner desativa o aviso automático, não a consulta nem a informação de versão.

No canal estável, a seleção ignora rascunhos e pré-lançamentos, ordena as versões por semver e procura da mais nova para a mais antiga um APK compatível. Isso evita depender do marcador `latest` do GitHub e permite usar o release mais recente que o dispositivo consegue instalar.

Em uma falha de rede, a tela conserva a última versão obtida durante a sessão e um update já conhecido continua disponível para download. Uma consulta bem-sucedida em uma próxima abertura ou ao tocar no botão atualiza essa informação.

Em 2026-10-30, a release estável mais recente é a v1.0.12: [abrir release](https://github.com/Pepeu2010/telumia/releases/tag/v1.0.12). Ela inclui APK universal, arm64-v8a, armeabi-v7a, x86 e x86_64, além da MSI Telumia Desktop 1.0.4. O workflow estável terminou com sucesso e os checksums foram publicados. O app Android consulta essa release como última versão estável.

## Incidente do login por QR

A release estável principal v1.0.11 contém o commit TV `4afb3243e83e42fd8423f2398a256da0ee523cdc`. A v1.0.12 inclui o código de geração QR do commit `debb535c5ab6607c7807ac6f85ac425e06b96f98`, mas a auditoria confirmou que o workflow daquela compilação não forneceu propriedades Supabase. Assim, não se deve tratar o QR da v1.0.12 como corrigido.

O código atualizado aceita também `SUPABASE_URL` e `SUPABASE_ANON_KEY` e mostra um erro explícito quando falta configuração. Por decisão do produto, a identidade visual continua Telumia e o login usa o backend oficial Nuvio: `https://api.nuvio.tv` com a publishable key pública descrita no [exemplo oficial Nuvio](https://github.com/NuvioMedia/self-host/blob/main/.env.example). Esses defaults agora entram diretamente no build; não dependem de secrets novas no GitHub Actions. A próxima release estável deve ser compilada e verificada com essa configuração.

O build local deste ambiente não possui `local.properties` nem credenciais de release. APKs Full Debug servem para validar compilação e interface; não substituem o pacote estável assinado pelo workflow.
