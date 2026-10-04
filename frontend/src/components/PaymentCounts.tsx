import type { EventDashboardResponse } from '../types';

const number = (value: number) => value.toLocaleString('it-IT');

export function PaymentCounts({ data }: { data: EventDashboardResponse }) {
    const cash = data.checkedInCashCount + data.walkInCashCount;
    const card = data.checkedInCardCount + data.walkInCardCount;
    const unrecorded = data.checkedInUnrecordedCount + data.walkInUnrecordedCount;
    const rows = [
        { label: 'Con prenotazione', cash: data.checkedInCashCount, card: data.checkedInCardCount, unrecorded: data.checkedInUnrecordedCount, total: data.checkedInCount },
        { label: 'Senza prenotazione', cash: data.walkInCashCount, card: data.walkInCardCount, unrecorded: data.walkInUnrecordedCount, total: data.walkInCount },
    ];
    const total = { label: 'Totale', cash, card, unrecorded, total: data.totalAttendees };
    return (
        <article className="panel payment-counts">
            <span className="eyebrow">Pagamenti all’ingresso</span>
            <h2>Contanti e carta</h2>
            <p>Persone entrate per metodo di pagamento.</p>
            <div className="table-shell payment-counts-table">
                <table className="data-table">
                    <caption>Ingressi con e senza prenotazione per metodo di pagamento</caption>
                    <thead><tr><th scope="col">Ingresso</th><th scope="col">Contanti</th><th scope="col">Carta / POS</th>{unrecorded > 0 && <th scope="col">Metodo non registrato</th>}<th scope="col">Totale</th></tr></thead>
                    <tbody>
                        {rows.map(row => <tr key={row.label}><th scope="row">{row.label}</th><td>{number(row.cash)}</td><td>{number(row.card)}</td>{unrecorded > 0 && <td>{number(row.unrecorded)}</td>}<td>{number(row.total)}</td></tr>)}
                    </tbody>
                    <tfoot><tr><th scope="row">Totale</th><td><strong>{number(cash)}</strong></td><td><strong>{number(card)}</strong></td>{unrecorded > 0 && <td>{number(unrecorded)}</td>}<td><strong>{number(data.totalAttendees)}</strong></td></tr></tfoot>
                </table>
            </div>
            <div className="payment-counts-cards">
                {[...rows, total].map(row => (
                    <section className={`payment-counts-card${row === total ? ' payment-counts-total' : ''}`} key={row.label}>
                        <h3>{row.label}</h3>
                        <dl>
                            <div><dt>Contanti</dt><dd>{number(row.cash)}</dd></div>
                            <div><dt>Carta / POS</dt><dd>{number(row.card)}</dd></div>
                            {unrecorded > 0 && <div><dt>Metodo non registrato</dt><dd>{number(row.unrecorded)}</dd></div>}
                            {row !== total && <div className="payment-counts-subtotal"><dt>Totale</dt><dd>{number(row.total)}</dd></div>}
                            {row === total && <div className="payment-counts-subtotal"><dt>Ingressi totali</dt><dd>{number(row.total)}</dd></div>}
                        </dl>
                    </section>
                ))}
            </div>
            {unrecorded > 0 && <p className="payment-hint">Per alcuni ingressi precedenti il metodo di pagamento non è stato registrato.</p>}
        </article>
    );
}
