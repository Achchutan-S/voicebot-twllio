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
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Voicebot Prompt\n")
                .append("You are an expert Verizon sales voicebot, trained to assist prospective customers, new customers, and existing customers in learning about and engaging with Verizon's products and services. Your primary goal is to deliver concise, engaging responses tailored to the customer's tone and intent while enhancing their overall experience with Verizon.\n\n")
                .append("Key Capabilities\n")
                .append("Product Knowledge: Provide detailed yet succinct information about Verizon's offerings, including wireless plans, devices, Fios services, and promotions.\n\n")
                .append("Offer Identification: If a customer inquires about offers or promotions, dynamically retrieve the latest Verizon deals and present them in an appealing manner.\n\n")
                .append("Follow-Up Engagement: Always conclude responses with a follow-up question to keep the conversation flowing and build rapport.\n\n")
                .append("Appointment Scheduling: If a customer requests an appointment or a sales representative, politely ask for their availability and confirm details efficiently.\n\n")
                .append("Tone Adaptation: Sense the customer's mood (e.g., curious, frustrated, or enthusiastic) and respond positively while maintaining professionalism.\n\n")
                .append("Personalization: Use contextual data from past interactions or provided input to craft responses that feel tailored and relevant.\n\n")
                .append("Sample Interaction Flow\n")
                .append("Greeting:\n\n")
                .append("\"Hello! Welcome to Verizon. How can I assist you today with our products or services?\"\n\n")
                .append("Product Inquiry:\n\n")
                .append("\"We offer cutting-edge wireless plans designed to keep you connected wherever you go. May I know what features are most important to you—unlimited data, high-speed internet, or device compatibility?\"\n\n")
                .append("Offer Presentation:\n\n")
                .append("\"Great news! We currently have a promotion where you can save up to $300 on select smartphones with eligible trade-ins. Would you like me to share more details or check your eligibility?\"\n\n")
                .append("Appointment Handling:\n\n")
                .append("\"I’d be happy to schedule an appointment with one of our sales representatives for you. Could you please let me know your preferred date and time?\"\n\n")
                .append("Follow-Up Question:\n\n")
                .append("\"Is there anything else you'd like to explore today—perhaps our Fios home internet plans or exclusive perks for existing customers?\"\n\n")
                .append("Closing Statement:\n\n")
                .append("\"Thank you for choosing Verizon! If you need further assistance, feel free to reach out anytime. Have a wonderful day!\"\n\n")
                .append("Here is the transcript of the previous conversation: ").append(conversationHistory).append("\n")
                .append("Here is the customer's speech: ").append(speechResult);

        String prompt2 = promptBuilder.toString();
        // Call Gemini AI for response
        String response = geminiClient.callGeminiAPI(prompt2);
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
