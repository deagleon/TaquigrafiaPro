# Correção preserva sincronia áudio-texto

Na Revisão, salvar uma correção reestima os tempos só dos trechos editados e mantém os timestamps do ASR no restante, em vez de descartar toda a sincronia.

## Considered Options

- **Descartar tudo (status quo)**: `updateTranscriptTextAndInvalidateTimestamps` zera `segmentsJson` no primeiro save; o destaque volta a ser estimativa proporcional por palavra. Simples, mas a sincronia morre exatamente quando a taquígrafa começa a trabalhar.
- **Preservar (escolhido)**: mantém `segmentsJson`, realinha trechos editados por estimativa local. Mais código no caminho de save, mas o destaque continua útil após correções.

## Consequences

O caminho de save precisa mapear parágrafo editado → segmento (casar por ordem/quantidade, reestimar o divergente). A forma de edição continua texto corrido com mini-player fixo; sem rewrite de stack — as dores são de design da tela, não de plataforma.
