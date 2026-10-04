package org.spring.passhalo.event.service;

import org.spring.passhalo.booking.enums.PaymentMethod;
import org.spring.passhalo.booking.exception.PaymentRegistrationException;
import org.spring.passhalo.event.entity.Event;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalkInCounterService {
    // Callers authorize the operation and load the event with PESSIMISTIC_WRITE.
    @Transactional(propagation = Propagation.MANDATORY)
    public void adjust(Event event, PaymentMethod paymentMethod, boolean increment) {
        if (paymentMethod == null) {
            throw new PaymentRegistrationException("Seleziona Contanti oppure Carta / POS", HttpStatus.BAD_REQUEST);
        }
        int count = paymentMethod == PaymentMethod.CASH ? event.getWalkInCashCount() : event.getWalkInCardCount();
        if (!increment && count <= 0) {
            throw new PaymentRegistrationException("Nessun ingresso da rimuovere per il metodo selezionato", HttpStatus.CONFLICT);
        }
        int delta = increment ? 1 : -1;
        if (paymentMethod == PaymentMethod.CASH) {
            event.setWalkInCashCount(Math.addExact(count, delta));
        } else {
            event.setWalkInCardCount(Math.addExact(count, delta));
        }
        event.setWalkInCount(Math.addExact(event.getWalkInCount(), delta));
    }
}
