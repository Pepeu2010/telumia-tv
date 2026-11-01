# Evolução visual e QA — 01/11/2026

Base: Desktop 6f39382b (distribuição 1.0.7) e TV d14cd632 (release 1.1.1). Trabalho nos aplicativos existentes, sem implementação paralela.

## Entrega visual

- Paleta Telumia compartilhada entre PC e TV: fundo grafite, superfícies azuladas e acentos âmbar/coral. Os temas personalizados continuam usando as próprias cores.
- Login com fundo cinematográfico alinhado à identidade Telumia, substituindo o roxo legado.
- Diálogos com superfícies em camadas; bordas, cartões e grupos de configurações com hierarquia e contraste consistentes.
- PC: filtros em formato de pílula, fundo de páginas com profundidade discreta, cabeçalhos de configurações com quebra controlada de títulos.
- TV: estados vazios com ícone destacado e texto centralizado; mensagens de erro com largura de leitura limitada; botão de tentar novamente com texto legível no foco.
- Player: painéis da TV usam as superfícies do tema. O restante dos controles herda os tokens existentes de foco, tipografia e movimento.
- Sem efeitos de blur ou loops adicionais. As políticas existentes de redução de movimento permanecem em uso.

## Correções investigadas

1. Diálogo TV: o cálculo anterior impunha altura mínima de 320 dp mesmo em telas menores. Agora os limites vêm da configuração disponível; largura também respeita as margens. Títulos podem ocupar duas linhas.
2. Diálogo PC: limite de largura aplicado antes de preencher o espaço, e espaçamento de ações limitado à largura disponível para evitar constraints negativas em janelas estreitas.
3. Pós-filme TV: um resultado já carregado não bloqueava a saída natural enquanto o overlay era avaliado. Voltar ao player também liberava a saída imediata. O estado agora preserva o player nessas transições; estado vazio continua permitindo conclusão normal.
4. WebP animado PC: o cartão aplicava um validador exclusivo de GIF ao WebP. Foi adicionado reconhecimento do cabeçalho VP8X com validação de dimensões antes do decoder nativo, preservando os limites de bytes, pixels e frames. O download acontece quando a animação será exibida, evitando prefetch de todos os cartões.

A validação de WebP segue a [especificação oficial do contêiner RIFF/VP8X](https://developers.google.com/speed/webp/docs/riff_container), documento sem versão semântica consultado em 01/11/2026. O MCP Grounded Docs não estava exposto nesta sessão; a fonte oficial foi consultada diretamente. Dependências preservadas: Desktop Compose Multiplatform 1.12.0/Kotlin 2.4.10 e TV Compose BOM 2026.05.01/Kotlin 2.3.0.

## Matriz de cobertura

| Área | Alteração visual nesta entrega | Verificação executada |
|---|---|---|
| Abertura/login | Paleta e fundo de identidade | Abertura real Android, QR gerado; testes de movimento desktop/QR Android |
| Home/continuar assistindo | Paleta e superfícies compartilhadas | Testes de Home, hero, hover e continuidade |
| Busca | Campos/filtros/cartões herdando tema | Testes de estado de busca e catálogo brasileiro |
| Biblioteca/coleções | Filtros, cartões e diálogos | Testes de biblioteca, listas e reconciliação de coleções |
| Perfis/editor/PIN/avatar | Superfícies e diálogos | Seleção, roteamento, confirmação de mutação, URL de avatar, editor e preferências protegidas |
| Filme/série/episódios | Paleta e diálogos | Hero de filme, ações, episódios e embaralhamento |
| Player/legendas/timeline | Painéis, diálogos e pós-filme | Regras de reprodução, timeline, Scene Info/bookmarks, superfícies nativas desktop |
| TV ao vivo/EPG | Paleta, painéis e diálogos | Programação, favoritos e troca por D-pad em fixtures |
| Addons/conta/sincronização | Superfícies compartilhadas | Reconciliação de addons, coleções e contratos; sem instalar fontes externas |
| Configurações/telas secundárias | Grupos com camadas, contornos e cabeçalhos | Componentes Zen/Horizon, reprodução, rastreamento, cache e responsividade |

A cobertura automatizada de componentes e contratos não representa navegação manual de cada rota, login aprovado por uma pessoa, reprodução de todos os provedores nem medição de desempenho em hardware físico. Os catálogos/streams de terceiros dependem dos próprios serviços. A MXQ física não foi usada nesta entrega.

## Execução e evidências

Resultados completos desta execução são registrados em `VISUAL_QA_RESULTS.json` e nas capturas de fixtures em `visual-qa-2026-11-01/`. Logs e dados temporários ficam em `artifacts/`, fora do commit. Os testes desktop de armazenamento usam APPDATA isolado e o sinalizador NUVIO_ENHANCED_ISOLATED_THEME_TEST=1; os testes de textos legados usam locale en-US.

A primeira execução ampla encontrou 38 falhas Desktop e 15 TV. Com o ambiente desktop isolado e locale definido, restaram 9: animações WebP/GIF, nome antigo da biblioteca, paths Linux no Windows, fixture de progresso de 1 segundo e expectativa da paleta anterior. As causas e alterações de fixtures estão separadas das correções de produção no diff.

TV: a execução final ampla contou 2.177 testes com 11 falhas que já estavam presentes na base, em testes de Media3/probe MKV, plugin por reflexão, padrões de poster, mock de catálogo, refresh Trakt e seleção de tracks. As duas regressões de pós-filme foram corrigidas. Não foi declarada aprovação integral da suíte TV.

Na instrumentação inicial Android 7, um teste de EPG falhou com o emulador em resolução de celular. Em resolução de TV, o teste passou sem mudar o código do EPG. A suíte final de interface usa Android 11 em 1920×1080 com densidade física 320. A leitura manual da tela confirmou que o QR é gerado e que o fluxo sem conta chega à Home.

## Resultado final desta entrega

- Desktop: **1.718 testes, 0 falhas, 1 ignorado**; `:composeApp:createDistributable` compilou o aplicativo Windows. A primeira abertura local estava sem configuração de backend; o pacote foi reconstruído com a mesma configuração pública oficial usada pelo workflow existente, sem alterar os defaults de produção.
- Android TV: `:app:assembleFullDebug` e `:app:assembleFullDebugAndroidTest` concluídos. A rodada de interface executou **102 testes, 101 aprovados e 1 falha** de isolamento da fixture de cache. Corrigido o ContextWrapper do teste para isolar também filesDir/codeCacheDir, os **10 testes da classe afetada passaram**. Todos os 102 casos têm resultado aprovado nas rodadas combinadas; não houve uma única rodada final de 102/102.
- TV unitários: **2.177 testes, 11 falhas persistentes desde a primeira execução**, listadas individualmente no JSON. A entrega não declara o app inteiro livre de bugs.
- As imagens anexas são fixtures determinísticas e não incluem QR de autenticação, tokens ou dados de conta pessoal. O diff das expectativas antigas de tema, nome de biblioteca, paths multiplataforma e duração de progresso é separado do código de produção.
- A atualização visual desta rodada atua sobretudo nos componentes e tokens compartilhados. Não representa reconstrução individual de todas as rotas ou validação manual integral de conta, addons e provedores.

### Abertura do pacote compilado

PC: processo isolado ativo e respondendo após 16 segundos, sem entradas Error/Exception no stderr após receber a configuração oficial do workflow. A execução foi oculta: isso não comprova navegação manual na janela nem login com conta real.

Android TV: processo ativo, hierarquia de interface na Home com catálogo e buffer de crashes vazio. O comando am start -W atingiu seu timeout de espera; a chegada à Home foi confirmada depois, sem alegar tempo de abertura aprovado ou desempenho medido.

### Capturas de componentes

- [live-tv-quick-channel-overlay.png](visual-qa-2026-11-01/live-tv-quick-channel-overlay.png)
- [telumia-home-hero-cta-tv.png](visual-qa-2026-11-01/telumia-home-hero-cta-tv.png)
- [telumia-settings-horizon-960x540dp.png](visual-qa-2026-11-01/telumia-settings-horizon-960x540dp.png)
- [telumia-settings-zen-640x360dp.png](visual-qa-2026-11-01/telumia-settings-zen-640x360dp.png)
