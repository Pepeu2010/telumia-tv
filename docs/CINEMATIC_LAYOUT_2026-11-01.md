# Evolução cinematográfica da composição — 01/11/2026

Entrega sobre as bases PC 7b886138 e TV 728f413b2. Implementa composição e navegação inspiradas nas cinco referências fornecidas pelo usuário, nos aplicativos existentes.

## PC

- A barra mantém o comportamento de navegação já validado, preservando o gesto do seletor de perfil e os modos existentes.
- Destaque com título/logo maior, sinopse com medida de leitura limitada, metadados reorganizados e nota IMDb visualmente separada. Só há nota quando a fonte fornece um número válido.
- A ação Ver detalhes fica à esquerda e seu texto permanece centralizado no botão arredondado.
- Paginação com posição real e áreas clicáveis maiores; navegação continua apontando para o título selecionado.
- O cabeçalho da TV desktop usa a paleta do tema; catálogo e ações de reprodução existentes permanecem conectados.
- O vídeo nativo Windows continua separado dos textos para respeitar a composição HWND validada na entrega anterior.

## TV e APK

- Hero de TV sem painel opaco: logo, título e sinopse ficam diretamente sobre a imagem e seu degradê. O modo de toque mantém uma base translúcida para leitura.
- Tipografia mais presente, logo maior, metadados secundários compactos e mais espaço entre o destaque e os carrosséis.
- Barra flutuante usa os destinos reais do drawer, incluindo TV. Seleção, foco e ação são estados separados; o menu abre o drawer existente. Respeita o modo de sidebar escolhido; a busca mantém seu fluxo de cabeçalho existente.
- Ver detalhes continua disponível durante a prévia. Começar o trailer preserva o foco de quem já estava no botão; a sinopse reduz levemente a opacidade, sem desaparecer.
- Gestão de fontes e fuso horário fica atrás de Fontes. A entrada de TV prioriza canais/programação; carregamento e erros continuam visíveis fora do editor.
- Guia, canais e programação continuam usando os dados dos addons/fontes configurados. Não foram acrescentados canais ou horários fictícios ao aplicativo.

## Referências técnicas

As [diretrizes oficiais de cartões](https://developer.android.com/design/ui/tv/guides/components/cards) e [foco para TV](https://developer.android.com/design/ui/tv/guides/styles/focus-system), consultadas em 01/11/2026, orientam proporções e a distinção entre selecionado/focado. São diretrizes sem versão semântica. Grounded Docs não está exposto nesta sessão. Versões locais: Compose Multiplatform 1.12.0/Kotlin 2.4.10 no PC e Compose BOM 2026.05.01/Kotlin 2.3.0 na TV. Nenhuma dependência foi acrescentada.

## Evidências e limites

Resultados desta rodada: CINEMATIC_LAYOUT_QA_RESULTS.json. A captura de composição PC utiliza componentes de produção e histórico de exemplo. Embora os arquivos de arte para Dune, Interstellar, Inception e Blade Runner 2049 tenham sido obtidos do catálogo Cinemeta para a fixture local, a captura verificada apresentou placeholders; o carregamento dessas imagens no teste ainda não foi confirmado. Nenhum desses dados ou arquivos é inserido no produto.

A MXQ física fica fora da validação. A suíte ampla anterior da TV mantém 11 falhas registradas em VISUAL_QA_RESULTS.json; a rodada focal não as declara resolvidas. Esta entrega não é um benchmark de hardware, uma validação de todos os provedores nem a conclusão de toda a reformulação visual. Não publica um release novo.

### Validação final

PC: 38 testes direcionados passaram e o aplicativo foi compilado com a configuração pública oficial. TV: cinco testes unitários e 18 testes de interface passaram no emulador Android 11. Na tela real de teste, a barra terminava em y=130 px e o título começava em y=160 px. O controle remoto selecionou TV, transferiu o foco para Fontes e abriu o formulário existente. A tela guest de teste não tinha addons de TV instalados, portanto canais reais da conta pessoal não foram avaliados.
