export interface ParsedSseEvent {
  event: string;
  data: string;
}

export class SseParser {
  private buffer = '';

  push(chunk: string): ParsedSseEvent[] {
    this.buffer = `${this.buffer}${chunk}`.replaceAll('\r\n', '\n').replace(/\r(?!$)/g, '\n');
    const events: ParsedSseEvent[] = [];
    let boundary = this.buffer.indexOf('\n\n');
    while (boundary >= 0) {
      const frame = this.buffer.slice(0, boundary);
      this.buffer = this.buffer.slice(boundary + 2);
      const parsed = parseFrame(frame);
      if (parsed) {
        events.push(parsed);
      }
      boundary = this.buffer.indexOf('\n\n');
    }
    return events;
  }

  finish(): ParsedSseEvent[] {
    const frame = this.buffer.replaceAll('\r', '\n');
    this.buffer = '';
    const parsed = parseFrame(frame);
    return parsed ? [parsed] : [];
  }
}

function parseFrame(frame: string): ParsedSseEvent | null {
  if (!frame || frame.startsWith(':')) {
    return null;
  }
  let event = 'message';
  const data: string[] = [];
  for (const line of frame.split('\n')) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trimStart();
    } else if (line.startsWith('data:')) {
      data.push(line.slice(5).trimStart());
    }
  }
  return data.length ? { event, data: data.join('\n') } : null;
}
