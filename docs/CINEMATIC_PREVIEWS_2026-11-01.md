# Prévias cinematográficas — 01/11/2026

Esta rodada continua a evolução visual dos aplicativos existentes, após bfc2b80c (PC) e 6347e6159 (TV). A meta visual ampla permanece ativa; esta entrega trata especificamente de trailers na Home e interação com som/foco.

## Referências e decisões

- [Netflix: prévias automáticas configuráveis](https://help.netflix.com/en/node/2102): referência de comportamento, mantendo controle por perfil e opção de desligar.
- [Android TV: sistema de foco](https://developer.android.com/design/ui/tv/guides/styles/focus-system): referência para preservar a seleção do controle remoto enquanto a mídia muda.
- [Android TV: cartões](https://developer.android.com/design/ui/tv/guides/components/cards): mídia em 16:9 e hierarquia separando imagem, título e ações.

Fontes oficiais consultadas em 01/11/2026, sem versão semântica da diretriz. Grounded Docs não estava disponível entre as ferramentas desta sessão. Não foram adicionadas dependências: PC usa Compose Multiplatform 1.12.0/Kotlin 2.4.10; TV usa Compose BOM 2026.05.01/Kotlin 2.3.0.

## Comportamento implementado

- PC: controle em pílula ao lado dos metadados da prévia dos cartões e no destaque da Home, com estado de áudio, ícone e ação acessível. Surge quando o player está pronto, desaparece ao terminar/falhar e não aciona o filme abaixo ao ser clicado.
- PC: ativar som vale para a prévia atual e não altera a preferência salva do perfil. Novas prévias continuam respeitando a configuração global.
- Windows: trocar mute não anexa novamente a fonte nem reinicia o trailer. O decoder prefere hardware e permite fallback por software; EOF imediato não é mais aceito como pronto. Fontes com áudio separado ainda seguem a proteção existente do backend nativo; não foi implementada mesclagem de faixas nesta rodada.
- Windows: o Canvas nativo não permite texto Compose sobre o vídeo. O controle dos cartões fica fora da imagem, e o destaque usa uma área de vídeo à direita, preservando título, ações, navegação e controle de áudio. As demais plataformas mantêm a composição sobre a imagem.
- TV: indicador de trailer/áudio só aparece após o primeiro quadro. Não recebe foco nem cria uma ação falsa no controle remoto.
- TV: a opção Hero Media funciona com pôsteres verticais sem exigir expansão de cartões. O destino Expanded Card continua exigindo um cartão apropriado. Layouts de toque não ganham hover artificial.
- TV: o controle de autoplay aparece em Configurações mesmo com pôster vertical sem expansão. Selecionar Expanded Card prepara a expansão necessária.
- TV: quando não existe escolha salva, prévias vêm ativadas e sem som. Um opt-out salvo continua valendo. Desligar expansão não modifica mais a preferência independente de trailer.
- Transições de entrada/saída do indicador (180/120 ms) e passagem de imagem para trailer (480 ms na TV) usam a política de movimento local. Sem blur adicional, animação infinita decorativa ou novos players em paralelo.
- Debounce, cancelamento ao navegar, limites de memória e pool único de player permanecem nos fluxos existentes.

## Validação e limites

Os resultados exatos desta rodada estão em CINEMATIC_PREVIEW_QA_RESULTS.json. As capturas são de mídia e dados de teste; não representam disponibilidade garantida de trailers de terceiros.

A suíte ampla anterior tinha 11 falhas unitárias persistentes na TV, registradas em VISUAL_QA_RESULTS.json. O resultado focal desta rodada não substitui uma nova execução completa nem elimina essas pendências. Não houve teste na MXQ física, login humano com conta real ou publicação de release nesta rodada.

### Evidência de reprodução

O teste anterior que esperava somente o callback de pronto não detectava vídeo ausente no Windows. A nova verificação amostra os pixels vermelhos/azuis de um AVI original local. Ela reproduziu a falha antes da correção e passou depois do fallback de decoder e da rejeição de EOF como pronto. Um teste adicional verifica que vídeo nativo à direita não cobre os pixels do título/ações à esquerda. As cores sólidas nas capturas são quadros de mídia de teste, não a proposta de aparência do aplicativo.

Na TV, o teste usa AVC original codificado localmente e o ModernHeroMediaLayer de produção com TrailerPlayerPool real. Aguarda o primeiro quadro, verifica o indicador sem ação de clique, conserva o foco em Ver detalhes e confirma a liberação do dono do player ao sair. Outro teste usa DirectionCenter para desligar autoplay em Configurações sem expandir os pôsteres. Não depende de trailers remotos ou da MXQ.

As verificações locais usam variantes internas de QA. Não foi criada versão de usuário, MSI público ou release novo. A suíte ampla da entrega anterior permanece documentada separadamente; os testes focais desta rodada não representam nova execução integral.

### Resultado final

- PC: **28 testes focais, 0 falhas, 0 ignorados** e createDistributable concluído. Rodada final: cinematic-preview-desktop-composed-ui-final.log. A verificação de composição mantém a janela de QA em primeiro plano, exige preenchimento de mais de 90% da área esperada pelo vídeo original e aguarda a repintura dos controles Compose.
- TV: **20 testes unitários e 16 testes de interface, todos aprovados**. Build fullDebug/AndroidTest concluído; rodada de interface em Android API 30, 1920×1080, densidade 320. Buffer de crashes vazio após a rodada. Logs: cinematic-preview-tv-settings-build.log e cinematic-preview-tv-settings-ui.log.
- As 11 falhas unitárias TV da suíte ampla anterior continuam abertas. Esta rodada não declara todas as áreas concluídas nem todos os fluxos pessoais/fornecedores validados.

Captura de AVC original, sem dados de conta: [prévia nativa TV](cinematic-previews-2026-11-01/tv-native-hero.png).
