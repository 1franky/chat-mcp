import { renderSafeMarkdown } from './safe-markdown';

describe('renderSafeMarkdown', () => {
  it('renders headings, code and tables', () => {
    const rendered = renderSafeMarkdown(
      '# Resultado\n\n```sql\nSELECT 1;\n```\n\n| campo | valor |\n| --- | --- |\n| uno | 1 |',
    );

    expect(rendered).toContain('<h1>Resultado</h1>');
    expect(rendered).toContain('class="language-sql"');
    expect(rendered).toContain('<table>');
    expect(rendered).toContain('<td>uno</td>');
  });

  it('escapes raw html and rejects dangerous links', () => {
    const rendered = renderSafeMarkdown(
      '<img src=x onerror=alert(1)> [abrir](javascript:alert(1)) [seguro](https://example.test)',
    );

    expect(rendered).not.toContain('<img');
    expect(rendered).not.toContain('javascript:');
    expect(rendered).toContain('&lt;img');
    expect(rendered).toContain('href="https://example.test"');
  });
});
