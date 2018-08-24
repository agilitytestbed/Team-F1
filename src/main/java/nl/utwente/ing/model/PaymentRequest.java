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
package nl.utwente.ing.model;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.TableGenerator;

@Entity
@Table(name = "paymentrequests")
public class PaymentRequest {

    @Id
    @GeneratedValue(generator="sqlite")
    @TableGenerator(name="sqlite", table="sqlite_sequence", pkColumnName="name", valueColumnName="seq", pkColumnValue="paymentrequests")
    private Integer id;

    @SerializedName("due_date")
    @Column(name = "due_date")
    private String dueDate;

    @SerializedName("number_of_requests")
    @Column(name = "number_of_requests")
    private int requestCount;

    private String description;
    private Long amount;

    @OneToMany(mappedBy = "paymentRequest")
    private List<Transaction> transactions;

    @ManyToOne(targetEntity = Session.class)
    @JoinColumn(name = "session_id")
    private Session session;

    public PaymentRequest() {}

    public PaymentRequest(String description, String dueDate, long amount, int requestCount) {
        this.description = description;
        this.dueDate = dueDate;
        this.amount = amount;
        this.requestCount = requestCount;
    }

    public Integer getId() {
        return id;
    }

    public String getDueDate() {
        return dueDate;
    }

    public String getDescription() {
        return description;
    }

    public int getRequestCount() {
        return requestCount;
    }

    public Long getAmount() {
        return amount;
    }

    public boolean isFilled() {
        return transactions != null && transactions.size() == requestCount;
    }

    public List<Transaction> getTransactions() {
        if (transactions == null) {
            return new ArrayList<>();
        } else {
            return transactions;
        }
    }

    public void setSession(Session session) {
        this.session = session;
    }
}
