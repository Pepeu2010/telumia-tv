# Ajustes de TV, celular e inicialização

**Data:** 30 de outubro de 2026

**Escopo:** tela inicial moderna, CTA do destaque, uso móvel e restauração da sessão Nuvio.

## O que mudou

- O CTA “Go to details” tem 136 dp e fica ancorado à esquerda do painel em TV, com o texto centralizado dentro do botão; em celular usa a largura útil completa e mantém o texto centralizado.
- A tela inicial usa um breakpoint de 600 dp. Em celulares, o destaque usa a largura inteira, margens de 16 dp e 92% da área útil para o texto. O espaço das fileiras e os cards diminuem para caber melhor na tela.
- A expansão de cards e a reprodução automática de trailers ficam desativadas no layout móvel. A imagem do logo do destaque recebe um tamanho de decodificação limitado à tela, reduzindo memória e trabalho de GPU.
- Na restauração, uma sessão Nuvio armazenada passa a ser publicada para a interface antes da validação remota. Sessões inválidas continuam sendo encerradas; falhas temporárias mantêm a conta conectada.
- Quando o endpoint de relatórios de autenticação não está configurado, o app deixa de tentar enviar/enfileirar relatórios e registra o envio como desativado. Isso remove o `IllegalStateException` secundário visto durante a inicialização local.

## Verificação

- `:app:testFullDebugUnitTest --tests 'com.nuvio.tv.core.diagnostics.DiagnosticRedactionTest' --tests 'com.nuvio.tv.ui.screens.home.ModernHomeLayoutMetricsTest'` — passou: 14 testes (12 de diagnóstico/redação e 2 de layout).
- `:app:assembleFullDebug` — passou. APK de validação x86: `app/build/outputs/apk/full/debug/app-full-x86-debug.apk`.
- AVD TV em 1920×1080, 320 dpi: o centro do texto do CTA ficou a 0,5 px do centro do botão.
- AVD em viewport móvel de 1080×2400, 420 dpi: texto do CTA no centro exato; destaque e cards cabem na largura útil. O AVD foi restaurado para 1920×1080, 320 dpi ao fim.
- O app debug abriu o QR de login Nuvio, entrou na tela inicial e não apresentou crash em `AndroidRuntime`. A instalação estável v1.0.0 foi mantida ao lado, sem apagar ou atualizar seus dados.

## Limites da validação

O AVD não tinha uma conta Nuvio autenticada. Portanto, a restauração de uma sessão real foi validada pela mudança do fluxo e pela compilação, mas não foi possível reproduzir com credenciais pessoais. A instalação estável preexistente é v1.0.0 e registrou uma tentativa antiga de conexão a `localhost`; a variante debug atual iniciou com os defaults oficiais Nuvio, sem essa falha. Nenhuma versão foi publicada.

## Revalidação do startup — 31/10/2026

- Os testes focados de `DiagnosticRedactionTest`, `ModernHomeLayoutMetricsTest`, `AuthSessionValidationPolicyTest`, `StartupAccountSurfaceSyncTest` e `AuthManagerStartupRestoreTest` passaram: 22 testes, 0 falhas. O novo teste mantém a validação remota pendente e confirma que a conta local já está disponível.
- `:app:assembleFullDebug :app:assembleFullDebugAndroidTest` concluiu com sucesso.
- No AVD Android TV API 33, `TelumiaHomeHeroTvTest` e `SettingsVisualTokensTest` passaram: 7 testes, 0 falhas em 1920×1080 e novamente 7 testes, 0 falhas em 1280×720. A seleção inclui a configuração de viewport móvel de 390 dp e valida o alvo amplo do botão.
- O AVD foi restaurado para 1920×1080, 320 dpi. Não foi testada a abertura com uma sessão Nuvio persistida real. A mudança permanece local, fora das releases estáveis `v1.0.13` e prévia `v1.0.14-alpha.1`.

## Referências visuais e de navegação

- [Android TV: sistema de foco](https://developer.android.com/design/ui/tv/guides/styles/focus-system)
- [Android TV: layouts](https://developer.android.com/design/ui/tv/guides/styles/layouts)
- [Android TV: carrossel em destaque](https://developer.android.com/design/ui/tv/guides/components/featured-carousel)
- [Android TV: navegação](https://developer.android.com/design/ui/tv/guides/foundations/navigation-on-tv)
