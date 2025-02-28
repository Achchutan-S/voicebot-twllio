package com.example.api.controller;

import com.example.api.client.GeminiClient;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Call;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@RestController
@RequestMapping("/twilio")
public class TwilioController {

    private static final String FILE_PATH = "conversation_history.txt";

    @Autowired
    private GeminiClient geminiClient;
    // Read Twilio credentials from application.properties
    @Value("${twilio.accountSid}")
    private String TWILIO_SID;

    @Value("${twilio.authToken}")
    private String TWILIO_AUTH_TOKEN;

    @Value("${twilio.phoneNumber}")
    private String TWILIO_PHONE_NUMBER;

    @PostConstruct
    public void init() {
        if (TWILIO_SID == null || TWILIO_AUTH_TOKEN == null || TWILIO_PHONE_NUMBER == null) {
            throw new IllegalStateException("Twilio credentials not set! Please check application.properties.");
        }
    }


    @GetMapping("/call")
    public String makeCall(@RequestParam("redirectUrl") String redirectUrl) {
        System.out.println("Initiating Twilio call...");

        if (TWILIO_SID == null || TWILIO_AUTH_TOKEN == null || TWILIO_PHONE_NUMBER == null) {
            return "Twilio credentials not set!";
        }

        Twilio.init(TWILIO_SID, TWILIO_AUTH_TOKEN);
        Call call = Call.creator(
                        new PhoneNumber("+917598299310"),
                        new PhoneNumber(TWILIO_PHONE_NUMBER),
                        URI.create(redirectUrl))
                .create();

        System.out.println("Call initiated: " + call.getSid());
        return "Call initiated successfully!";
    }

    @PostMapping("/twilio-start")
    public ResponseEntity<String> startCall() {
        System.out.println("Initiated Twilio start");

        String twimlResponse = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Response>
                    <Say>Hi, I am an AI assistant calling from Verizon. I have an exciting offer on iPhone 16 specially curated for you. Would you like to know about it?</Say>
                    <Gather input="speech" action="/twilio/twilio-process" method="POST" speechTimeout="auto"/>
                </Response>
                """;

        return ResponseEntity.ok().body(twimlResponse);
    }

    @PostMapping("/twilio-process")
    public ResponseEntity<String> processSpeech(@RequestParam("SpeechResult") String speechResult) {
        System.out.println("User said: " + speechResult);

        String reply;
        if (speechResult == null || speechResult.trim().isEmpty()) {
            reply = "Sorry, I didn't catch that. Can you please repeat?";
        } else if (speechResult.toLowerCase().contains("bye")) {
            reply = "Goodbye! Have a great day.";
            return ResponseEntity.ok().body(buildTwiml(reply, true));
        } else if (speechResult.toLowerCase().contains("yes") &&
                speechResult.toLowerCase().contains("know") &&
                speechResult.toLowerCase().contains("about")) {
            reply = "We are currently offering an exciting iPhone deal, including trade-in discounts, promotions, and flexible financing options. Do you have any questions?";
        } else if (speechResult.toLowerCase().contains("offers") ||
                speechResult.toLowerCase().contains("iphone") ||
                speechResult.toLowerCase().contains("15")) {
            reply = "We have special offers on the iPhone 15 series, with limited-time deals. Would you like to schedule an appointment with our sales representative?";
        } else if (speechResult.toLowerCase().contains("yes") &&
                speechResult.toLowerCase().contains("appointment")) {
            reply = "Awesome! What would be your preferred time for the meeting?";
        } else if (speechResult.toLowerCase().contains("coming") ||
                speechResult.toLowerCase().contains("week") ||
                speechResult.toLowerCase().contains("monday")) {
            reply = "Great! We will reach out to you with the meeting details scheduled for Monday. Say 'goodbye' if you want to end this call.";
        } else {
            reply = "I'm sorry, I couldn't understand that. Can you please repeat or rephrase your request?";
        }

        return ResponseEntity.ok().body(buildTwiml(reply, false));
    }

    @PostMapping("/twilio-processWithGemini")
    public ResponseEntity<String> processWithGemini(@RequestParam("SpeechResult") String speechResult) {
        System.out.println("User said: " + speechResult);

        // Read previous conversation history
        String conversationHistory = readConversationHistory();

        // Construct prompt for Gemini API
        String prompt = "You are an expert Verizon sales bot. Your job is to make conversation with prospective customers about Verizon products and services."
                + " If they ask for an offer, try finding an offer from Verizon and frame it in an answer."
                + " Keep answers short and engaging, adding a follow-up question to continue the conversation."
                + " If the user asks for an appointment, ask about their availability."
                + " Here is the transcript of the previous conversation: " + conversationHistory
                + " Here is the customer's speech: " + speechResult;

        // Call Gemini AI for response
        String response = geminiClient.callGeminiAPI(prompt);
        System.out.println("Bot response: " + response);

        if (response == null || response.isEmpty()) {
            return ResponseEntity.ok().body(buildTwiml("Sorry, some error occurred on our end. We will reach out to you again later.", true));
        }

        // Update conversation history
        String updatedHistory = conversationHistory + "Customer: " + speechResult + "\nBot: " + response + "\n";
        writeConversationHistory(updatedHistory);

        return ResponseEntity.ok().body(buildTwiml(response, false));
    }

    private String buildTwiml(String message, boolean endCall) {
        if (endCall) {
            return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Response>
                    <Say>%s</Say>
                    <Hangup/>
                </Response>
                """.formatted(message);
        } else {
            return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Response>
                    <Say>%s</Say>
                    <Gather input="speech" action="/twilio/twilio-processWithGemini" method="POST" speechTimeout="auto"/>
                </Response>
                """.formatted(message);
        }
    }

    private void writeConversationHistory(String content) {
        try {
            Files.write(Paths.get(FILE_PATH), content.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.err.println("Error writing conversation history: " + e.getMessage());
        }
    }

    private String readConversationHistory() {
        try {
            return Files.readString(Paths.get(FILE_PATH));
        } catch (IOException e) {
            return "";
        }
    }
}
