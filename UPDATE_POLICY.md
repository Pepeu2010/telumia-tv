# Atualizador estável para Android TV

O Telumia TV consulta `GET /repos/{owner}/{repo}/releases/latest` ao iniciar e também oferece uma verificação manual em **Sobre > Verificar atualizações**. Ele não monitora o GitHub em segundo plano com o app fechado.

Somente releases publicadas e estáveis são elegíveis. Além do filtro do GitHub, o app rejeita releases marcadas como draft ou prerelease e tags ou títulos com alpha, beta, RC ou preview. Preferências antigas de canal beta são migradas para estável; a interface não oferece seleção de pré-lançamentos.

Ao selecionar **Atualizar**, uma tela modal mostra o progresso do APK. Quando o download termina, o app abre o instalador do Android. O Android pode exigir a permissão para instalar apps desta fonte e uma confirmação do sistema; por segurança, o app não instala APKs silenciosamente.

Para uma atualização aparecer, publique uma release estável com tag sem sufixo de pré-lançamento, por exemplo `v1.0.3`, desative **Set as a pre-release** e inclua um APK compatível ou universal assinado com a mesma chave e identidade de pacote das versões instaladas. Releases alpha/beta não serão oferecidas.
