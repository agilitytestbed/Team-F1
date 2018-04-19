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

public class CategoryRule {

    private Integer id;
    private String description;
    @SerializedName("iBAN")
    private String iban;
    private String type; // A String is used instead of the "Type" object as other values can be used here as well.
    @SerializedName("category_id")
    private Integer categoryId;
    private boolean applyOnHistory;

    /**
     * Constructor to create a CategoryRule.
     * Fields can be left empty to match any input in a corresponding Transaction field.
     *
     * @param id CategoryRule id - should be generated by the database and set by Gson.
     * @param description Transaction description to match. Can be left empty to match any input.
     * @param iban Transaction externalIBAN to match. Can be left empty to match any input.
     * @param type Transaction type to match. Can be left empty to match any input.
     * @param categoryId Category to assign to the transaction if it matches the specified fields.
     * @param applyOnHistory determines whether this CategoryRule should be retroactively applied to older Transactions.
     */
    public CategoryRule(Integer id, String description, String iban, String type, int categoryId, boolean applyOnHistory) {
        this.id = id;
        this.description = description;
        this.iban = iban;
        this.type = type;
        this.categoryId = categoryId;
        this.applyOnHistory = applyOnHistory;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public String getIban() {
        return iban;
    }

    public String getType() {
        return type;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public boolean shouldApplyOnHistory() {
        return applyOnHistory;
    }
}