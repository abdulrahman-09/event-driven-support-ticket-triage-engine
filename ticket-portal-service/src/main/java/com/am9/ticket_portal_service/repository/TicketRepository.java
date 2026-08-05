package com.am9.ticket_portal_service.repository;

import com.am9.ticket_portal_service.entity.Ticket;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface TicketRepository extends MongoRepository<Ticket, String> {

}
