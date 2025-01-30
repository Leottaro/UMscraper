# UMscraper

UMscraper est un programme Java qui récupère vos notes de la section `Mon dossier > Notes & Résultats` du portail ENT de l'Université de Montpellier et vous notifie par email en cas de changement.

## Setup

### Prérequis

-   Java 11 ou plus
-   Maven
-   Firefox
-   Un compte Gmail (désolé Alban)

### Variables d'environnement

le fichier `.env` à la racine du projet doit contenir :

```
ENT_LOGIN_EMAIL=""
ENT_PASSWORD=""
GECKODRIVER_PATH=""
GMAIL_LOGIN_EMAIL=""
GMAIL_PASSWORD=""
FROM_EMAIL="" # optionnel
TO_EMAIL=""
```

### Remplir les variables d'environnement

1. **ENT_LOGIN_EMAIL**: Votre adresse email de l'Université de Montpellier.

    - Exemple : `ENT_LOGIN_EMAIL="nom.prenom@etu.umontpellier.fr"`

2. **ENT_PASSWORD**: Votre mot de passe de l'Université de Montpellier.

    - Exemple : `ENT_PASSWORD="votre_mot_de_passe"`

3. **GECKODRIVER_PATH**: Le chemin absolu vers l'exécutable Geckodriver.

    - Exemple : `GECKODRIVER_PATH="/chemin/vers/geckodriver"`
    - Vous pouvez télécharger Geckodriver [ici](https://github.com/mozilla/geckodriver/releases/) et le placer où vous le souhaitez.

4. **GMAIL_LOGIN_EMAIL**: L'adresse email du compte Gmail qui enverra les notifications.

    - Exemple : `GMAIL_LOGIN_EMAIL="votre.email@gmail.com"`

5. **GMAIL_PASSWORD**: Le mot de passe de application Gmail.

    - Exemple : `GMAIL_PASSWORD="votre_mot_de_passe_app"`
    - Note : Vous devez générer un mot de passe d'application depuis les paramètres de votre compte Google. Suivez [ce guide](https://support.google.com/accounts/answer/185833?hl=fr) pour générer un mot de passe d'application.

6. **FROM_EMAIL**: L'adresse email qui apparaîtra comme expéditeur des notifications.

    - Exemple : `FROM_EMAIL="bot@votredomaine.com"`
    - Si non spécifié, `GMAIL_LOGIN_EMAIL` sera utilisé.

7. **TO_EMAIL**: L'adresse email qui recevra les notifications.
    - Exemple : `TO_EMAIL="votre.email@gmail.com"`

### Exécution de l'application

1. Construisez le projet en utilisant Maven :

    ```
    mvn clean package
    ```

2. Exécutez l'application :
    ```
    java -jar target/UMscraper-1.0-jar-with-dependencies.jar
    ```

L'application va scraper les notes du compte spécifié toutes les heures. Si un changement est détecté, les nouvelles notes seront envoyées à l'adresse email spécifiée.

### Dépannage

-   **Problèmes de connexion** : Assurez-vous que vos identifiants ENT sont corrects et que vous pouvez vous connecter manuellement.
-   **Problèmes avec Geckodriver** : Vérifiez que le chemin vers Geckodriver est correct et que l'exécutable est accessible.
-   **Problèmes d'envoi d'email** : Assurez-vous que vous avez généré un mot de passe d'application pour votre compte Gmail et que les paramètres de votre compte permettent l'envoi d'emails via SMTP.

Pour toute autre question ou problème, veuillez consulter la documentation ou ouvrir une issue sur le dépôt GitHub.
