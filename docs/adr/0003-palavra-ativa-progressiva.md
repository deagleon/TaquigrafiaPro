# Palavra ativa só quando o provedor entrega tempos de palavra

O destaque base da Revisão é sempre o Trecho ativo (funciona em todo provider/modelo, ver ADR-0001). A Palavra ativa é realce adicional dentro do Trecho ativo, visível só quando há timestamps de palavra — Gemini/multimodal/chat nunca entregam; whisper/chirp só se pedirmos granularidade word.

## Considered Options

- **Só Trecho, sempre (rejeitado)**: consistente, mas abre mão da precisão onde o dado existe.
- **Palavra quando disponível (escolhido)**: Trecho garante o piso consistente; palavra é progressivo onde dá.

## Consequences

O pedido verbose passa a incluir granularidade word onde suportado; a UI trata palavra como opcional (nunca como fonte de navegação — clique-para-ouvir continua por Trecho). Leituras futuras não devem "corrigir" a ausência de palavra em modelos sem timestamps: é limite do provedor, não bug.
