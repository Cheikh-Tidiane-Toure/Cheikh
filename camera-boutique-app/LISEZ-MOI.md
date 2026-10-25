# 📹 Caméra Boutique (application Android)

Transforme un téléphone Android en caméra de surveillance qui **continue de filmer en arrière-plan, écran éteint, même si tu fermes l'application**. Tu regardes l'image en direct depuis ton PC ou ton téléphone avec un navigateur, protégée par mot de passe.

> ⚠️ Ce projet a été écrit mais **n'a pas pu être compilé ni testé ici**. Si la compilation affiche une erreur, copie le message et envoie-le-moi : je corrige.

## 1. Obtenir l'application (APK)

### Méthode A : sans rien installer (GitHub, gratuit)
1. Crée un compte sur github.com, puis un nouveau dépôt (« New repository »).
2. Envoie tous les fichiers de ce dossier dans le dépôt (bouton « Add file » → « Upload files »). Le dossier caché `.github` doit être envoyé aussi.
3. Va dans l'onglet **Actions**. La construction démarre toute seule (3 à 6 minutes).
4. Quand c'est vert ✅, clique sur la construction, puis télécharge **CameraBoutique-APK** tout en bas. Dézippe : tu obtiens `app-debug.apk`.

### Méthode B : avec Android Studio
Ouvre ce dossier dans Android Studio, attends la synchronisation, puis menu **Build → Build APK(s)**.

## 2. Installer sur le téléphone de la boutique
1. Copie `app-debug.apk` sur le téléphone et ouvre-le.
2. Android demande d'autoriser l'installation depuis cette source : accepte.
3. Si Play Protect avertit, choisis « Installer quand même » (l'application n'est pas publiée sur le Play Store).

## 3. Utiliser
1. Ouvre **Caméra Boutique**. Un mot de passe est déjà généré : note-le ou change-le.
2. Appuie sur **« 🔋 Autoriser en arrière-plan »** et accepte. C'est important pour que le téléphone ne tue pas l'application.
3. Appuie sur **« Démarrer la caméra »** et autorise la caméra et les notifications.
4. L'application affiche des liens du type `http://192.168.1.20:8080`.
5. Tu peux maintenant fermer l'application et éteindre l'écran. Une notification « Caméra Boutique active » reste affichée : c'est normal, c'est elle qui permet de continuer. Laisse le téléphone sur le chargeur.

## 4. Regarder
- **Dans la boutique (même Wi-Fi)** : ouvre le lien dans un navigateur. Identifiant : `boutique`, puis le mot de passe.
- **De loin** : installe **Tailscale** (gratuit) sur le téléphone de la boutique et sur ton PC ou ton téléphone, avec le **même compte**. Ouvre ensuite `http://100.x.x.x:8080` en utilisant l'adresse Tailscale du téléphone de la boutique (affichée aussi dans l'application, c'est celle qui commence par `100.`). Ne mets **pas** le port 8080 en accès direct sur internet.
- Sur la page : **🔄 changer de caméra** (avant/arrière), **📸 photo**.

## 5. À savoir
- Sans personne qui regarde, l'application économise la batterie (1 image toutes les 2 s).
- Il n'y a **pas d'enregistrement** ni d'alerte de mouvement pour l'instant. Je peux les ajouter.
- Certaines marques (Xiaomi, Huawei, Samsung, Oppo…) ferment les applications en arrière-plan. Dans les réglages du téléphone, mets Caméra Boutique en « Batterie : sans restriction » et verrouille-la dans la liste des applications récentes.
- Si la caméra s'arrête après un redémarrage du téléphone, rouvre l'application et appuie sur Démarrer (Android interdit le démarrage automatique d'une caméra).
- À utiliser dans **ta** boutique. Si des clients ou employés sont filmés, un panneau « Zone sous vidéosurveillance » est conseillé, et les règles locales peuvent s'appliquer.
