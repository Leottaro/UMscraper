package fr.leotta.ro;

import io.github.cdimascio.dotenv.Dotenv;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class App {

    public static void main(String[] args) throws IOException {
        File dotenv_file = new File(".env");
        if (!dotenv_file.exists()) {
            System.err.format(
                ".env file not found in the current directory, created one at %s\n",
                dotenv_file.getAbsolutePath()
            );
            FileWriter writer = new FileWriter(dotenv_file);
            writer.write(
                "ENT_USERNAME=\"\"\nENT_PASSWORD=\"\"\nNOTES_PATH=\"\"\nGECKODRIVER_PATH=\"\"\nGMAIL_USERNAME=\"\"\nGMAIL_PASSWORD=\"\"\nFROM_EMAIL=\"\"\nTO_EMAIL=\"\""
            );
            writer.close();
            throw new RuntimeException("Please fill the .env file with the required information");
        }
        Dotenv dotenv = Dotenv.configure().load();

        while (true) {
            scrapNotes(dotenv);
            try {
                Thread.sleep(Duration.ofMinutes(60).toMillis());
            } catch (InterruptedException e) {
                e.printStackTrace();
                return;
            }
        }
    }

    public static boolean scrapNotes(Dotenv dotenv) {
        String ent_username = dotenv.get("ENT_USERNAME");
        String ent_password = dotenv.get("ENT_PASSWORD");
        String gmail_username = dotenv.get("GMAIL_USERNAME");
        String gmail_password = dotenv.get("GMAIL_PASSWORD");
        String from_email = dotenv.get("FROM_EMAIL");
        String to_email = dotenv.get("TO_EMAIL");
        String notes_path = Paths
            .get(dotenv.get("NOTES_PATH").replaceFirst("^~", System.getProperty("user.home")))
            .toAbsolutePath()
            .normalize()
            .toString();

        // Set the path to geckodriver if not in system PATH
        System.setProperty("webdriver.gecko.driver", "/Users/leoh/.cargo/bin/geckodriver");

        // Configure Firefox in headless mode
        FirefoxOptions options = new FirefoxOptions();
        options.addArguments("--headless"); // Run without GUI

        // Initialize WebDriver
        WebDriver driver = new FirefoxDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        try {
            // Navigate to the login page
            driver.get("https://ent.umontpellier.fr");

            // Locate and fill the username and password fields
            WebElement usernameField = wait.until(
                ExpectedConditions.visibilityOfElementLocated(By.id("username"))
            );
            WebElement passwordField = wait.until(
                ExpectedConditions.visibilityOfElementLocated(By.id("password"))
            );

            usernameField.sendKeys(ent_username);
            passwordField.sendKeys(ent_password);

            // Submit the form
            WebElement loginButton = wait.until(
                ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[type=submit]"))
            );
            loginButton.click();
            System.out.println("Login successful");

            // Wait for login to process
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

            // Access a protected page after login
            driver.get("https://app.umontpellier.fr/mdw/#!notesView"); // Change URL accordingly

            WebElement L3Informatique = wait.until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("[class=\"v-slot v-slot-link v-slot-v-link\"]")
                )
            );
            L3Informatique.click();
            System.out.println("L3 Informatique clicked");

            WebElement divParentTableau = wait.until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector(
                        "[class=\"v-table v-widget scrollabletable v-table-scrollabletable v-has-width v-has-height\"]"
                    )
                )
            );

            ArrayList<ArrayList<String>> notes = new ArrayList<ArrayList<String>>();
            List<WebElement> rows = divParentTableau.findElements(By.tagName("tr"));
            for (WebElement row : rows) {
                List<WebElement> cols = row.findElements(By.tagName("td"));
                String code = cols.get(0).getText().trim();
                if (!code.startsWith("HAI5") && !code.startsWith("HAL5")) {
                    continue;
                }
                String description = cols.get(1).getText().trim();
                String session1 = cols.get(2).getText();
                if (!session1.contains("/")) {
                    continue;
                }
                session1 = session1.split("/")[0].trim();
                notes.add(new ArrayList<String>(Arrays.asList(code, description, session1)));
            }
            String notesString = notes
                .stream()
                .map(note -> String.join(";", note))
                .reduce("", (acc, note) -> acc + note + "\n");
            System.out.println("Notes fetched: \n" + notesString);

            // write the notes
            new File(notes_path).mkdirs();

            File file = new File(notes_path + "/notes.csv");
            if (!file.exists()) {
                file.createNewFile();
            }

            // read the previous number of notes
            FileReader reader = new FileReader(file);
            BufferedReader bufferedReader = new BufferedReader(reader);
            int lineCount = 0;
            while (bufferedReader.readLine() != null) {
                lineCount++;
            }
            bufferedReader.close();
            reader.close();

            // write the notes
            FileWriter writer = new FileWriter(file);
            writer.write("Code;Description;Session 1\n");
            writer.write(notesString);
            writer.close();

            // create the notes_changed file if the number of notes has changed
            file = new File(notes_path + "/notes_changed");
            if (lineCount != notes.size() + 1) {
                file.createNewFile();
                sendMail(gmail_username, gmail_password, from_email, to_email, notesString);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            driver.quit(); // Close the browser
        }
        return false;
    }

    public static void sendMail(
        String gmail_username,
        String gmail_password,
        String from_email,
        String to_email,
        String content
    ) {
        // Set up properties for the mail session
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        // Create a session with an authenticator
        Session session = Session.getInstance(
            props,
            new javax.mail.Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(gmail_username, gmail_password);
                }
            }
        );

        try {
            // Create a new email message
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from_email));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to_email));
            message.setSubject("UMscraper Notification");
            message.setText(content);

            // Send the email
            Transport.send(message);
            System.out.println("Email sent successfully");
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }
}
