import type { EventDashboardResponse } from '../types';

const number = (value: number) => value.toLocaleString('it-IT');

export function PaymentCounts({ data }: { data: EventDashboardResponse }) {
    const cash = data.checkedInCashCount + data.walkInCashCount;
    const card = data.checkedInCardCount + data.walkInCardCount;
    const unrecorded = data.checkedInUnrecordedCount + data.walkInUnrecordedCount;
    return (
        <article className="panel payment-counts">
            <span className="eyebrow">Pagamenti all’ingresso</span>
            <h2>Contanti e carta</h2>
            <p>Persone entrate per metodo di pagamento.</p>
            <div className="table-shell">
                <table className="data-table">
                    <caption>Ingressi con e senza prenotazione per metodo di pagamento</caption>
                    <thead><tr><th scope="col">Ingresso</th><th scope="col">Contanti</th><th scope="col">Carta / POS</th>{unrecorded > 0 && <th scope="col">Metodo non registrato</th>}<th scope="col">Totale</th></tr></thead>
                    <tbody>
                        <tr><th scope="row">Con prenotazione</th><td>{number(data.checkedInCashCount)}</td><td>{number(data.checkedInCardCount)}</td>{unrecorded > 0 && <td>{number(data.checkedInUnrecordedCount)}</td>}<td>{number(data.checkedInCount)}</td></tr>
                        <tr><th scope="row">Senza prenotazione</th><td>{number(data.walkInCashCount)}</td><td>{number(data.walkInCardCount)}</td>{unrecorded > 0 && <td>{number(data.walkInUnrecordedCount)}</td>}<td>{number(data.walkInCount)}</td></tr>
                    </tbody>
                    <tfoot><tr><th scope="row">Totale</th><td><strong>{number(cash)}</strong></td><td><strong>{number(card)}</strong></td>{unrecorded > 0 && <td>{number(unrecorded)}</td>}<td><strong>{number(data.totalAttendees)}</strong></td></tr></tfoot>
                </table>
            </div>
            {unrecorded > 0 && <p className="payment-hint">Per alcuni ingressi precedenti il metodo di pagamento non è stato registrato.</p>}
        </article>
    );
}
