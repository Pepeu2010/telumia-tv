# Política de segurança — Telumia

## Versões e manutenção

As correções de segurança são priorizadas para o código atual da branch principal. Versões antigas podem precisar de atualização para receber as correções.

## Como relatar uma vulnerabilidade

**Não abra uma issue ou discussão pública** para falhas exploráveis, dados pessoais ou credenciais expostas.

1. Use o recurso **Security > Advisories > Report a vulnerability** deste repositório, caso o relato privado esteja habilitado.
2. Se não estiver disponível, contate o mantenedor por um canal privado antes de divulgar detalhes técnicos.
3. Inclua passos mínimos de reprodução, impacto, versão/commit afetado e uma correção sugerida, se houver. **Nunca envie senhas, chaves reais ou dados de terceiros.**

Não existe prazo de resposta garantido. O mantenedor avaliará o impacto, corrigirá a falha e coordenará a divulgação quando apropriado.

## Orientações para contribuição

- Nunca faça commit de tokens, arquivos `.env`, credenciais de assinatura, chaves privadas ou dados reais de usuários.
- Use os **GitHub Actions Secrets** ou **Environments** para credenciais de builds e releases; utilize o mínimo de permissões necessário.
- Mantenha dependências atualizadas e revise os pull requests do Dependabot antes do merge.
- Execute as verificações de segurança e os testes apropriados antes de alterar a branch principal.
- Se uma credencial for exposta, **revogue ou rotacione a credencial**: apagá-la em um commit posterior não invalida cópias do histórico Git.

> Verificações automáticas ajudam a encontrar problemas, mas não substituem revisão de código, configuração de proteção de branches e gerenciamento de acessos no GitHub.
