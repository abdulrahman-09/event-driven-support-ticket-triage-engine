package com.am9.ticket_ingestion_service.repository;

import com.am9.ticket_ingestion_service.model.Ticket;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketRepository extends MongoRepository<Ticket, String> {

}
