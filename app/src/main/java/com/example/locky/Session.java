package com.example.locky;

import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

@IgnoreExtraProperties

public class Session {

    private String Email;
    private @ServerTimestamp
    Date timestamp;


    public Session(String Email, Date timestamp) {
        this.Email = Email;
        this.timestamp = timestamp;
    }

    public Session() {
    }

    public String getEmail() {
        return Email;
    }

    public void setEmail(String Email) {
        this.Email = Email;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

}
