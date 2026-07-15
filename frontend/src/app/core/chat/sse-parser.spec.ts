import { SseParser } from './sse-parser';

describe('SseParser', () => {
  it('reassembles fragmented events and ignores heartbeat comments', () => {
    const parser = new SseParser();

    expect(parser.push(': keepalive\n\n')).toEqual([]);
    expect(parser.push('event: del')).toEqual([]);
    expect(parser.push('ta\ndata: {"type":"delta"}\n\n')).toEqual([
      { event: 'delta', data: '{"type":"delta"}' },
    ]);
  });

  it('joins multiline data fields', () => {
    const parser = new SseParser();

    expect(parser.push('event: message\ndata: first\ndata: second\n\n')).toEqual([
      { event: 'message', data: 'first\nsecond' },
    ]);
  });

  it('normalizes a CRLF boundary split between network chunks', () => {
    const parser = new SseParser();

    expect(parser.push('event: delta\r')).toEqual([]);
    expect(parser.push('\ndata: {"delta":"ok"}\r\n\r\n')).toEqual([
      { event: 'delta', data: '{"delta":"ok"}' },
    ]);
  });
});
