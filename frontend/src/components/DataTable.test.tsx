import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { DataTable } from './DataTable';

interface Row {
  code: string;
  hours: number;
}

const columns = [
  { label: 'Code', render: (row: Row) => row.code },
  { label: 'Uren', render: (row: Row) => row.hours.toFixed(2) }
];

describe('DataTable', () => {
  it('should_render_placeholder_when_rows_are_empty', () => {
    render(<DataTable rows={[]} columns={columns} />);

    expect(screen.getByText('Geen gegevens')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('should_render_custom_placeholder_when_provided', () => {
    render(<DataTable rows={[]} columns={columns} empty="Nog geen weekstaten" />);

    expect(screen.getByText('Nog geen weekstaten')).toBeInTheDocument();
  });

  it('should_render_one_row_per_item_with_rendered_cells', () => {
    render(
      <DataTable
        rows={[
          { code: 'MW-001', hours: 8 },
          { code: 'MW-002', hours: 0.25 }
        ]}
        columns={columns}
        testId="employees"
      />
    );

    expect(screen.getByTestId('employees')).toBeInTheDocument();
    expect(screen.getAllByRole('row')).toHaveLength(3); // kop + twee regels
    expect(screen.getByText('MW-002')).toBeInTheDocument();
    expect(screen.getByText('0.25')).toBeInTheDocument();
  });
});
