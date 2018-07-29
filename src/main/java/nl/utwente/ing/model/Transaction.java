/*
 * Copyright (c) 2018, Joost Prins <github.com/joostprins>, Tom Leemreize <https://github.com/oplosthee>
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
package nl.utwente.ing.model;

import javax.persistence.*;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @Column(name = "transaction_id")
    @GeneratedValue(generator="sqlite")
    @TableGenerator(name="sqlite", table="sqlite_sequence", pkColumnName="name", valueColumnName="seq", pkColumnValue="transactions")
    private Integer id;

    private String date;
    private Long amount;
    private String description;

    @Column(name = "external_iban")
    private String externalIBAN;

    @Enumerated(EnumType.STRING)
    private Type type;

    @ManyToOne(targetEntity = Category.class)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(targetEntity = Session.class)
    //@JoinColumn(name = "session_id", insertable = false, updatable = false)
    @JoinColumn(name = "session_id")
    private Session session;

    public Transaction() {}

    /**
     * Constructor to create a transaction without a category.
     * @param id transaction id
     * @param amount transaction amount in cents
     */
    public Transaction(Integer id, String date, Long amount, String description, String externalIBAN, Type type) {
        this(id, date, amount, description, externalIBAN, type,null);
    }

    /**
     * Constructor to create a transaction with a category.
     * @param id transaction id
     * @param amount transaction amount in cents
     * @param externalIBAN IBAN number of the sender/receiver
     * @param type type of transaction
     * @param category transaction category
     */
    public Transaction(Integer id, String date, Long amount, String description, String externalIBAN, Type type, Category category) {
        this.id = id;
        this.date = date;
        this.amount = amount;
        this.description = description;
        this.externalIBAN = externalIBAN;
        this.type = type;
        this.category = category;

    }

    public Integer getId() {
        return id;
    }

    public String getDate() {
        return date;
    }

    public Long getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public String getExternalIBAN() {
        return externalIBAN;
    }

    public Category getCategory() {
        return category;
    }

    public Type getType() {
        return type;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setExternalIBAN(String externalIBAN) {
        this.externalIBAN = externalIBAN;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }
}
