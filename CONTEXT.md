# Taquigrafia Pro

App Android de transcrição de sessões plenárias para taquígrafas: importa o áudio, gera a transcrição via STT e oferece revisão com áudio sincronizado.

## Language

**Transcrição**:
O texto produzido pelo provedor de STT a partir do áudio de uma sessão.
_Avoid_: texto, ata, transcriçao

**Revisão**:
O trabalho da taquígrafa na tela Visualizar Transcrição: ouvir o áudio, acompanhar o texto e corrigir.
_Avoid_: edição, correção

**Trecho**:
A unidade de sincronia áudio↔texto e de navegação na Revisão: um parágrafo com início/fim, vindos do ASR ou estimados, clicável para ouvir.
_Avoid_: parágrafo, segmento

**Segmento**:
O intervalo com timestamps retornado pelo provedor de STT; insumo da sincronia, não unidade de exibição.
_Avoid_: trecho


**Salto**:
Voltar ou avançar segundos fixos no áudio; o padrão é 5s.
_Avoid_: pular, skip, ±10s

**Retrocesso**:
Voltar automaticamente ~1,5s ao retomar após pausa, devolvendo o contexto da frase.
_Avoid_: auto-rewind, voltar atrás

**Laço**:
Intervalo do áudio (ex. 01:23→01:29) que repete enquanto a taquígrafa corrige.
_Avoid_: loop, repeat, repetição

**Forma de onda**:
Visualização dos picos do áudio na Revisão; mostra pausas e trocas de fala para navegar.
_Avoid_: waveform, gráfico de áudio

**Palavra ativa**:
A palavra sendo ouvida, destacada só quando o provedor entrega tempos de palavra; o Trecho ativo destaca sempre.
_Avoid_: palavra atual