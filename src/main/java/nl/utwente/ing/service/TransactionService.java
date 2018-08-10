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
package nl.utwente.ing.service;

import nl.utwente.ing.model.Category;
import nl.utwente.ing.model.Session;
import nl.utwente.ing.model.Transaction;
import nl.utwente.ing.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;

    @Autowired
    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public Transaction add(Transaction transaction) {
        if (transaction.getCategory() == null) {
            transactionRepository.addTransactionWithoutCategory(
                    transaction.getDate(),
                    transaction.getAmount(),
                    transaction.getDescription(),
                    transaction.getExternalIBAN(),
                    transaction.getType().name(),
                    transaction.getSession().getSessionID()
            );

            return transactionRepository.findByIdAndSession(transactionRepository.findLastId(), transaction.getSession());
        } else {
            return transactionRepository.save(transaction);
        }
    }

    @Transactional
    public List<Transaction> findBySession(Session session) {
        return transactionRepository.findBySessionOrderByDateDesc(session);
    }

    @Transactional
    public List<Transaction> findBySessionAsc(Session session) {
        return transactionRepository.findBySessionOrderByDateAsc(session);
    }

    @Transactional
    public List<Transaction> findBySession(Session session, int offset, int limit) {
        if (offset == 0 && limit == 0) {
            return findBySession(session);
        }

        OffsetLimitPageable pageable = new OffsetLimitPageable(offset, limit);
        return transactionRepository.findBySessionOrderByDateDesc(session, pageable);
    }

    @Transactional
    public List<Transaction> findBySessionAndCategoryName(Session session, String categoryName) {
        return transactionRepository.findBySessionAndCategoryNameOrderByDateDesc(session, categoryName);
    }

    @Transactional
    public List<Transaction> findBySessionAndCategoryName(Session session, String categoryName, int offset, int limit) {
        if (offset == 0 && limit == 0) {
            return findBySessionAndCategoryName(session, categoryName);
        }

        OffsetLimitPageable pageable = new OffsetLimitPageable(offset, limit);
        return transactionRepository.findBySessionAndCategoryNameOrderByDateDesc(session, categoryName, pageable);
    }

    @Transactional
    public Transaction findByIdAndSession(int id, Session session) {
        return transactionRepository.findByIdAndSession(id, session);
    }

    @Transactional
    public Transaction findFirstByOrderByDateDesc() {
        return transactionRepository.findFirstByOrderByDateDesc();
    }

    @Transactional
    public int update(Transaction transaction) {
        return transactionRepository.updateTransaction(
                transaction.getDate(),
                transaction.getAmount(),
                transaction.getDescription(),
                transaction.getExternalIBAN(),
                transaction.getType(),
                transaction.getId(),
                transaction.getSession()
        );
    }

    @Transactional
    public int updateCategory(Transaction transaction, Category category) {
        return transactionRepository.updateTransactionCategory(category, transaction.getId(), transaction.getSession());
    }

    @Transactional
    public int delete(int id, Session session) {
        return transactionRepository.deleteByIdAndSession(id, session);
    }

    public class OffsetLimitPageable extends PageRequest {

        private int offset;

        OffsetLimitPageable(int offset, int limit) {
            super(offset, limit);
            this.offset = offset;
        }

        @Override
        public long getOffset() {
            return offset;
        }
    }
}
