# Picos da forma de onda calculados na importação

A Revisão mostra a Forma de onda do áudio para navegar por pausas e trocas de fala. Os picos vêm de decodificação no aparelho (MediaPlayer não expõe amplitude) e são calculados uma vez na importação, com cache — não na abertura.

## Considered Options

- **Na importação (escolhido)**: import fica segundos mais lento uma vez (sessões de 26min+); toda Revisão depois abre instantânea.
- **Na primeira abertura**: import intacto, mas a primeira Revisão de cada áudio demora — justo na hora em que a taquígrafa quer trabalhar.

## Consequences

O pipeline de importação ganha etapa de decode + escrita do cache de picos (formato do cache vira contrato de armazenamento). Funciona para todo áudio, independente do provedor de STT, porque picos vêm do sinal — não de timestamps.
