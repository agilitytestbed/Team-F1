/*
 * Copyright (c) 2018, Tom Leemreize <https://github.com/oplosthee>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package nl.utwente.ing.repository;

import nl.utwente.ing.model.Category;
import nl.utwente.ing.model.Session;
import nl.utwente.ing.model.Transaction;
import nl.utwente.ing.model.Type;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findBySessionOrderByDateAsc(Session session);

    List<Transaction> findBySessionOrderByDateDesc(Session session);

    List<Transaction> findBySessionOrderByDateDesc(Session session, Pageable pageable);

    List<Transaction> findBySessionAndCategoryNameOrderByDateDesc(Session session, String categoryName);

    List<Transaction> findBySessionAndCategoryNameOrderByDateDesc(Session session, String categoryName, Pageable pageable);

    Transaction findByIdAndSession(int id, Session session);

    Transaction findFirstByOrderByDateDesc();

    @Query(value = "SELECT last_insert_rowid() FROM transactions LIMIT 1", nativeQuery =  true)
    int findLastId();

    @Modifying
    @Query(value = "INSERT INTO transactions (date, amount, description, external_iban, category_id, type, session_id) VALUES " +
            "(:date, :amount, :description, :externalIban, (SELECT category_id " +
            "FROM categoryrules " +
            "WHERE (description = :description OR description = '') " +
            "AND (iban = :externalIban OR iban = '') " +
            "AND (type = :type OR type = '') " +
            "AND (category_id IS NOT NULL) " +
            "AND (session_id = :sessionId) " +
            "LIMIT 1), " +
            ":type, :sessionId)", nativeQuery = true)
    int addTransactionWithoutCategory(@Param("date") String date, @Param("amount") Long amount,
                                      @Param("description") String description, @Param("externalIban") String iban,
                                      @Param("type") String type, @Param("sessionId") String sessionId);

    @Modifying
    @Query("UPDATE Transaction SET date = :date, amount = :amount, description = :description, " +
            "externalIBAN = :externalIban, type = :type WHERE id = :id AND session = :session")
    int updateTransaction(@Param("date") String date, @Param("amount") Long amount,
                          @Param("description") String description, @Param("externalIban") String iban,
                          @Param("type") Type type, @Param("id") int id, @Param("session") Session session);

    @Modifying
    @Query("UPDATE Transaction SET category = :category WHERE id = :id AND session = :session")
    int updateTransactionCategory(@Param("category") Category category, @Param("id") int id, @Param("session") Session session);

    int deleteByIdAndSession(int id, Session session);
}
