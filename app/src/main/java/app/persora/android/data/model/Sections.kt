package app.persora.android.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.ui.graphics.vector.ImageVector

/** Port of SECTION_DEFINITIONS in src/data.ts. Field keys are the metadata keys stored on the server. */
enum class FieldKind { TEXT, EMAIL, URL, DATE, TEXTAREA, SELECT, NUMBER }

data class FieldDefinition(
    val key: String,
    val label: String,
    val kind: FieldKind = FieldKind.TEXT,
    val options: List<String> = emptyList(),
    val placeholder: String = "",
    val required: Boolean = false,
    val wide: Boolean = false,
)

data class SectionDefinition(
    val id: String,
    val label: String,
    val eyebrow: String,
    val singular: String,
    val description: String,
    val icon: ImageVector,
    val color: String,
    val titleLabel: String,
    val fields: List<FieldDefinition>,
    val previewKeys: List<String>,
    val dateKey: String? = null,
)

private fun f(key: String, label: String, kind: FieldKind = FieldKind.TEXT, options: List<String> = emptyList(), placeholder: String = "", required: Boolean = false, wide: Boolean = false) =
    FieldDefinition(key, label, kind, options, placeholder, required, wide)

val CURRENCIES = listOf("BDT", "USD", "EUR", "GBP", "INR", "AED", "SGD", "CAD", "AUD", "JPY", "CNY", "SAR", "Other")

object Sections {
    val all: List<SectionDefinition> = listOf(
        SectionDefinition(
            id = "documents", label = "Documents", eyebrow = "Personal vault", singular = "document",
            description = "Keep identity, health and important paperwork together, with expiry dates never out of sight.",
            icon = Icons.Outlined.Badge, color = "blue", titleLabel = "Document title", dateKey = "expiryDate",
            previewKeys = listOf("type", "documentNumber", "expiryDate"),
            fields = listOf(
                f("title", "Document title", placeholder = "e.g. Bangladesh passport", required = true),
                f("type", "Document type", FieldKind.SELECT, listOf("National ID / NID", "Passport", "Birth certificate", "Student ID", "Job ID / Employee ID", "Driving licence", "Tax ID / TIN", "Visa", "Residence permit", "Work permit", "Health card", "Insurance", "Certificate", "Contract", "CV / Resume", "Other"), required = true),
                f("name", "Name"),
                f("studentName", "Student name"),
                f("documentNumber", "Document number", placeholder = "Optional"),
                f("studentId", "Student ID"),
                f("roll", "Roll number"),
                f("registrationNumber", "Registration number"),
                f("dateOfBirth", "Date of birth", FieldKind.DATE),
                f("fatherName", "Father's name"),
                f("motherName", "Mother's name"),
                f("address", "Address", FieldKind.TEXTAREA, wide = true),
                f("institutionName", "Institute name"),
                f("institutionAddress", "Institute address", FieldKind.TEXTAREA, wide = true),
                f("phone", "Phone"),
                f("bloodGroup", "Blood group", FieldKind.SELECT, listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-", "Other")),
                f("nationality", "Nationality"),
                f("placeOfBirth", "Place of birth"),
                f("passportType", "Passport type"),
                f("passportNumber", "Passport number"),
                f("visaNumber", "Visa number"),
                f("country", "Country"),
                f("visaType", "Visa type"),
                f("permitNumber", "Permit number"),
                f("employer", "Employer / organization"),
                f("employeeId", "Employee ID"),
                f("jobTitle", "Job title / occupation"),
                f("department", "Department"),
                f("licenseClass", "Licence class"),
                f("taxOffice", "Tax office"),
                f("insurer", "Insurance provider"),
                f("memberId", "Member ID"),
                f("policyNumber", "Policy number"),
                f("policyType", "Policy type"),
                f("coverage", "Coverage"),
                f("startDate", "Start date", FieldKind.DATE),
                f("issuingAuthority", "Issuing authority"),
                f("partyName", "Other party"),
                f("contractNumber", "Contract number"),
                f("issueDate", "Issue date", FieldKind.DATE),
                f("expiryDate", "Expiry date", FieldKind.DATE),
                f("member", "Belongs to", FieldKind.SELECT, listOf("Me")),
                f("targetRole", "Target role", placeholder = "e.g. Product Designer"),
                f("email", "Contact email", FieldKind.EMAIL),
                f("location", "Location", placeholder = "City, country"),
                f("portfolio", "LinkedIn / portfolio", FieldKind.URL, placeholder = "https://"),
                f("summary", "Professional summary", FieldKind.TEXTAREA, wide = true),
                f("experience", "Experience", FieldKind.TEXTAREA, wide = true),
                f("education", "Education", FieldKind.TEXTAREA, wide = true),
                f("skills", "Skills", FieldKind.TEXTAREA, wide = true),
                f("notes", "Notes", FieldKind.TEXTAREA, placeholder = "Add a reminder or any useful details", wide = true),
            ),
        ),
        SectionDefinition(
            id = "academics", label = "Academics", eyebrow = "Education records", singular = "academic record",
            description = "Build a tidy archive of certificates, transcripts, results and the milestones behind them.",
            icon = Icons.Outlined.School, color = "violet", titleLabel = "Record title",
            previewKeys = listOf("type", "institution", "year"),
            fields = listOf(
                f("title", "Record title", placeholder = "e.g. BSc in Computer Science", required = true),
                f("type", "Record type", FieldKind.SELECT, listOf("SSC / Secondary certificate", "HSC / Higher secondary certificate", "Diploma", "Bachelor's degree", "Master's degree", "PhD", "Mark sheet", "Transcript", "Certificate", "Admission record", "Admission letter", "Admission payment slip", "Admission application", "Exam document", "Training record", "Other"), required = true),
                f("institution", "Institution", placeholder = "School, college or university"),
                f("studentName", "Student name"),
                f("fatherName", "Father's name"),
                f("motherName", "Mother's name"),
                f("dateOfBirth", "Date of birth", FieldKind.DATE),
                f("studentId", "Student ID"),
                f("roll", "Roll number"),
                f("registrationNumber", "Registration number"),
                f("phone", "Phone"),
                f("address", "Address", FieldKind.TEXTAREA, wide = true),
                f("institutionAddress", "Institute address", FieldKind.TEXTAREA, wide = true),
                f("year", "Year completed", placeholder = "e.g. 2024"),
                f("grade", "Grade / result", placeholder = "Optional"),
                f("program", "Program / course", placeholder = "e.g. BSc in Computer Science"),
                f("admissionSession", "Admission session", placeholder = "e.g. Fall 2026"),
                f("applicationNumber", "Application / student ID"),
                f("paymentAmount", "Payment amount", placeholder = "e.g. ৳ 15,000"),
                f("paymentDate", "Payment date", FieldKind.DATE),
                f("notes", "Notes", FieldKind.TEXTAREA, wide = true),
            ),
        ),
        SectionDefinition(
            id = "subscriptions", label = "Subscriptions", eyebrow = "Recurring expenses", singular = "subscription",
            description = "See what renews, what it costs and which payment method is attached to every plan.",
            icon = Icons.Outlined.CreditCard, color = "orange", titleLabel = "Service name", dateKey = "renewalDate",
            previewKeys = listOf("plan", "price", "renewalDate"),
            fields = listOf(
                f("title", "Service name", placeholder = "e.g. Spotify", required = true),
                f("plan", "Plan", placeholder = "e.g. Individual"),
                f("price", "Price", placeholder = "e.g. ৳ 219 / month"),
                f("cycle", "Billing cycle", FieldKind.SELECT, listOf("Monthly", "Quarterly", "Yearly", "Weekly", "One-time", "Other")),
                f("startDate", "Start date", FieldKind.DATE),
                f("renewalDate", "Next renewal", FieldKind.DATE),
                f("payment", "Payment method", placeholder = "e.g. Visa •••• 4821"),
                f("accountEmail", "Account email", FieldKind.EMAIL, placeholder = "name@example.com"),
                f("website", "Login website", FieldKind.URL, placeholder = "https://"),
                f("notes", "Notes", FieldKind.TEXTAREA, wide = true),
            ),
        ),
        SectionDefinition(
            id = "family", label = "Family", eyebrow = "Family records", singular = "family member",
            description = "Keep the people you care about connected to the documents and details that matter to them.",
            icon = Icons.Outlined.Favorite, color = "rose", titleLabel = "Full name",
            previewKeys = listOf("relationship", "dateOfBirth", "notes"),
            fields = listOf(
                f("title", "Full name", placeholder = "e.g. Samira Rahman", required = true),
                f("relationship", "Relationship", FieldKind.SELECT, listOf("Spouse", "Father", "Mother", "Child", "Brother / Sister", "Grandparent", "Other")),
                f("dateOfBirth", "Date of birth", FieldKind.DATE),
                f("email", "Email", FieldKind.EMAIL, placeholder = "Optional"),
                f("phone", "Phone", placeholder = "Optional"),
                f("notes", "Notes", FieldKind.TEXTAREA, placeholder = "Important context or reminders", wide = true),
            ),
        ),
        SectionDefinition(
            id = "purchases", label = "Purchases & warranty", eyebrow = "Purchase vault", singular = "purchase record",
            description = "Invoices, serial numbers and warranty dates—right where you can find them when you need them.",
            icon = Icons.Outlined.ShoppingBag, color = "blue", titleLabel = "Product name", dateKey = "warrantyExpiry",
            previewKeys = listOf("brand", "purchaseDate", "warrantyExpiry"),
            fields = listOf(
                f("title", "Product name", placeholder = "e.g. MacBook Air 15-inch", required = true),
                f("brand", "Brand"), f("model", "Model"), f("serial", "Serial / IMEI"), f("seller", "Seller / store"),
                f("purchaseDate", "Purchase date", FieldKind.DATE),
                f("price", "Purchase price", placeholder = "e.g. ৳ 145,000"),
                f("invoiceNumber", "Invoice number"),
                f("warrantyExpiry", "Warranty expires", FieldKind.DATE),
                f("notes", "Notes", FieldKind.TEXTAREA, wide = true),
            ),
        ),
        SectionDefinition(
            id = "accounts", label = "Accounts", eyebrow = "Account index", singular = "account",
            description = "Keep online account references and bank details organized. Never store passwords, PINs, CVVs, or one-time codes here.",
            icon = Icons.Outlined.Key, color = "indigo", titleLabel = "Account name",
            previewKeys = listOf("bankAccountType", "branch", "accountType", "username", "email"),
            fields = listOf(
                f("title", "Account name", placeholder = "e.g. Google or Primary savings", required = true),
                f("accountKind", "Account kind", FieldKind.SELECT, listOf("Internet Account", "Bank Account"), required = true),
                f("accountType", "Internet account type", FieldKind.SELECT, listOf("Facebook", "Instagram", "LinkedIn", "X", "TikTok", "YouTube", "WhatsApp", "Telegram", "GitHub", "Pinterest", "Google", "Gmail", "Microsoft", "Apple", "Amazon", "Netflix", "Spotify", "Discord", "Reddit", "PayPal", "Email", "Social media", "Banking / payments", "Shopping", "Education", "Freelance", "Work", "Cloud services", "Developer", "Gaming", "Streaming", "Government", "Business", "Other")),
                f("username", "Username"),
                f("email", "Account email", FieldKind.EMAIL),
                f("website", "Login / online banking URL", FieldKind.URL, placeholder = "https://"),
                f("registered", "Registration date", FieldKind.DATE),
                f("status", "Account status", FieldKind.SELECT, listOf("Active", "Paused", "Closed", "Review needed")),
                f("bankName", "Bank name", placeholder = "e.g. City Bank"),
                f("accountHolder", "Account holder"),
                f("bankAccountType", "Bank account type", FieldKind.SELECT, listOf("Savings", "Current", "Fixed deposit", "Salary", "Student", "Business", "Mobile wallet", "Other")),
                f("accountNumber", "Account number", placeholder = "Enter the account number"),
                f("currency", "Currency", FieldKind.SELECT, CURRENCIES),
                f("branch", "Branch"), f("routingNumber", "Routing number"), f("swiftCode", "SWIFT / BIC"), f("iban", "IBAN"), f("mobileBanking", "Mobile banking service"),
                f("notes", "Notes", FieldKind.TEXTAREA, wide = true),
            ),
        ),
        SectionDefinition(
            id = "personal-finance", label = "Personal Finance", eyebrow = "Money, clearly organized", singular = "finance record",
            description = "Understand income, spending, assets and debt, and connect money records to the people and things they relate to.",
            icon = Icons.Outlined.AccountBalanceWallet, color = "blue", titleLabel = "Record name",
            previewKeys = listOf("financeType", "amount", "currency", "transactionDate"),
            fields = listOf(
                f("title", "Record name", required = true),
                f("financeType", "Finance type", FieldKind.SELECT, listOf("income", "expense", "loan", "asset"), required = true),
                f("amount", "Amount", FieldKind.NUMBER, required = true),
                f("currency", "Currency"),
                f("transactionDate", "Transaction date", FieldKind.DATE),
                f("category", "Category"), f("counterparty", "Paid to / received from"),
                f("accountReference", "Account or wallet"), f("dueDate", "Due date", FieldKind.DATE),
                f("interestRate", "Interest rate"), f("assetType", "Asset type"),
                f("notes", "Notes", FieldKind.TEXTAREA, wide = true),
            ),
        ),
        SectionDefinition(
            id = "memberships", label = "Membership wallet", eyebrow = "Digital wallet", singular = "membership",
            description = "Keep membership IDs, loyalty cards and renewal dates ready in one digital wallet.",
            icon = Icons.Outlined.CreditCard, color = "blue", titleLabel = "Membership name", dateKey = "expiryDate",
            previewKeys = listOf("organization", "memberId", "expiryDate"),
            fields = listOf(
                f("title", "Membership name", placeholder = "e.g. City Library", required = true),
                f("type", "Membership type", FieldKind.SELECT, listOf("Gym", "Library", "Student", "Professional", "Club", "Loyalty", "Retail", "Travel", "Insurance", "Gaming", "Event", "Community", "Other")),
                f("organization", "Organization"), f("memberId", "Member ID / card number"),
                f("studentName", "Student name"), f("studentId", "Student ID"), f("program", "Program / course"),
                f("phone", "Phone"), f("address", "Address", FieldKind.TEXTAREA, wide = true),
                f("startDate", "Start date", FieldKind.DATE), f("expiryDate", "Expiry date", FieldKind.DATE),
                f("level", "Membership level", placeholder = "e.g. Gold"),
                f("website", "Website", FieldKind.URL, placeholder = "https://"),
                f("notes", "Notes", FieldKind.TEXTAREA, wide = true),
            ),
        ),
        SectionDefinition(
            id = "wallet-cards", label = "Wallet Cards", eyebrow = "Payment card wallet", singular = "wallet card",
            description = "Keep a visual, masked reference to your cards in any currency. Only the brand and last four digits are saved—never a full card number or security code.",
            icon = Icons.Outlined.Wallet, color = "blue", titleLabel = "Cardholder name",
            previewKeys = listOf("network", "issuer", "currency"),
            fields = listOf(
                f("title", "Cardholder name", placeholder = "Name printed on the card", required = true),
                f("cardNumber", "Card number", placeholder = "Enter full card number to detect brand"),
                f("network", "Card network · auto-detected"),
                f("expiry", "Expiry · MM/YY", placeholder = "00/00", required = true),
                f("cardType", "Card type", FieldKind.SELECT, listOf("Debit", "Credit", "Prepaid", "Travel", "Other")),
                f("issuer", "Bank / issuer", placeholder = "e.g. City Bank"),
                f("currency", "Currency", FieldKind.SELECT, CURRENCIES),
                f("notes", "Note", FieldKind.TEXTAREA, wide = true),
            ),
        ),
        SectionDefinition(
            id = "study", label = "Study materials", eyebrow = "Study space", singular = "study material",
            description = "Bring notes, books, lecture slides and saved links into one searchable study space.",
            icon = Icons.Outlined.MenuBook, color = "sky", titleLabel = "Material name",
            previewKeys = listOf("course", "subject", "materialType"),
            fields = listOf(
                f("title", "Material name", placeholder = "e.g. Week 04 — Color systems", required = true),
                f("course", "Course"), f("subject", "Subject"), f("chapter", "Chapter / topic"),
                f("materialType", "Material type", FieldKind.SELECT, listOf("PDF", "Notes", "Lecture slides", "Book", "Document", "Image", "Audio", "Video", "Assignment", "Question paper", "Solution", "Research paper", "Link", "Personal notes")),
                f("author", "Author / instructor"), f("publisher", "Publisher / source"),
                f("publicationDate", "Published on", FieldKind.DATE), f("sourceUrl", "Source URL", FieldKind.URL, placeholder = "https://"),
                f("semester", "Semester / year"),
                f("tags", "Tags", placeholder = "Separate tags with commas"),
                f("notes", "Notes", FieldKind.TEXTAREA, wide = true),
            ),
        ),
        SectionDefinition(
            id = "notes", label = "Tasks & Notes", eyebrow = "Tasks, notes & reminders", singular = "note",
            description = "",
            icon = Icons.Outlined.StickyNote2, color = "yellow", titleLabel = "Note title",
            previewKeys = listOf("tags"),
            fields = listOf(
                f("title", "Note title", placeholder = "e.g. Ideas for the weekend", required = true),
                f("content", "Note", FieldKind.TEXTAREA, placeholder = "Write your note here…", wide = true),
                f("tags", "Tags", placeholder = "Separate tags with commas", wide = true),
            ),
        ),
        SectionDefinition(
            id = "business-card", label = "Digital business card", eyebrow = "Your digital identity", singular = "digital card",
            description = "A polished, shareable profile for the moments you want to make a great introduction.",
            icon = Icons.Outlined.BusinessCenter, color = "slate", titleLabel = "Full name",
            previewKeys = listOf("jobTitle", "company", "email"),
            fields = listOf(
                f("title", "Full name", placeholder = "Your name", required = true),
                f("jobTitle", "Job title"), f("company", "Company"),
                f("bio", "Short bio", FieldKind.TEXTAREA, wide = true),
                f("phone", "Phone"), f("email", "Email", FieldKind.EMAIL),
                f("website", "Website", FieldKind.URL, placeholder = "https://"),
                f("linkedin", "LinkedIn", FieldKind.URL, placeholder = "https://linkedin.com/in/…"),
                f("instagram", "Instagram", FieldKind.URL, placeholder = "https://instagram.com/…"),
                f("location", "Location"),
            ),
        ),
        SectionDefinition(
            id = "urls", label = "URL manager", eyebrow = "Quick links", singular = "saved URL",
            description = "The useful links you never want to search for twice, neatly organized and ready to open.",
            icon = Icons.Outlined.Link, color = "cyan", titleLabel = "Link name",
            previewKeys = listOf("urlCategory", "url", "tags"),
            fields = listOf(
                f("title", "Link name", placeholder = "e.g. Portfolio", required = true),
                f("url", "URL", FieldKind.URL, placeholder = "https://example.com", required = true),
                f("urlCategory", "Category", FieldKind.SELECT, listOf("Personal website", "Portfolio", "Social profile", "LinkedIn", "GitHub", "YouTube", "Payment link", "Business", "Booking", "Government portal", "Job portal", "Education portal", "Other")),
                f("owner", "Owner / service"),
                f("tags", "Tags", placeholder = "Separate tags with commas"),
                f("description", "Description", FieldKind.TEXTAREA, wide = true),
                f("notes", "Notes", FieldKind.TEXTAREA, wide = true),
            ),
        ),
    )

    val byId: Map<String, SectionDefinition> = all.associateBy { it.id }
    operator fun get(id: String): SectionDefinition = byId[id] ?: all.first()

    private val additionalDataField = f("additionalData", "Additional Data", FieldKind.TEXTAREA, placeholder = "Other information from this record", wide = true)

    /** Type-aware editor schema shared by the Android item form and its Smart Scan field definitions. */
    fun editorFields(sectionId: String, type: String = "", accountKind: String = "", financeType: String = "", materialType: String = ""): List<FieldDefinition> {
        val base = get(sectionId).fields
        val normalizedType = type.trim().lowercase()
        val keys: Set<String>? = when (sectionId) {
            "documents" -> when {
                normalizedType.contains("nid") || normalizedType.contains("national id") || normalizedType.contains("national identity") -> setOf("title", "type", "name", "documentNumber", "dateOfBirth", "fatherName", "motherName", "address", "issueDate", "expiryDate", "bloodGroup", "member", "phone", "notes")
                normalizedType.contains("student") -> setOf("title", "type", "institutionName", "studentName", "fatherName", "motherName", "dateOfBirth", "studentId", "roll", "registrationNumber", "phone", "address", "institutionAddress", "program", "year", "issueDate", "expiryDate", "notes")
                normalizedType.contains("passport") -> setOf("title", "type", "name", "documentNumber", "passportType", "nationality", "dateOfBirth", "placeOfBirth", "issueDate", "expiryDate", "member", "notes")
                normalizedType.contains("birth certificate") -> setOf("title", "type", "name", "documentNumber", "dateOfBirth", "fatherName", "motherName", "address", "issueDate", "member", "notes")
                normalizedType.contains("job id") || normalizedType.contains("employee") -> setOf("title", "type", "name", "employeeId", "employer", "jobTitle", "department", "phone", "issueDate", "expiryDate", "member", "notes")
                normalizedType.contains("driving") || normalizedType.contains("licen") -> setOf("title", "type", "name", "documentNumber", "licenseClass", "dateOfBirth", "bloodGroup", "address", "issueDate", "expiryDate", "phone", "notes")
                normalizedType.contains("tax") || normalizedType.contains("tin") -> setOf("title", "type", "name", "documentNumber", "taxOffice", "address", "issueDate", "phone", "notes")
                normalizedType.contains("visa") -> setOf("title", "type", "name", "passportNumber", "visaNumber", "country", "visaType", "dateOfBirth", "issueDate", "expiryDate", "notes")
                normalizedType.contains("residence") -> setOf("title", "type", "name", "permitNumber", "nationality", "address", "issueDate", "expiryDate", "phone", "notes")
                normalizedType.contains("work permit") -> setOf("title", "type", "name", "permitNumber", "employer", "jobTitle", "passportNumber", "country", "issueDate", "expiryDate", "notes")
                normalizedType.contains("health card") -> setOf("title", "type", "name", "insurer", "memberId", "policyNumber", "phone", "address", "issueDate", "expiryDate", "notes")
                normalizedType.contains("insurance") -> setOf("title", "type", "name", "insurer", "policyNumber", "policyType", "coverage", "startDate", "expiryDate", "phone", "address", "notes")
                normalizedType.contains("certificate") -> setOf("title", "type", "name", "documentNumber", "issuingAuthority", "issueDate", "expiryDate", "member", "notes")
                normalizedType.contains("contract") -> setOf("title", "type", "partyName", "employer", "contractNumber", "startDate", "expiryDate", "phone", "address", "notes")
                normalizedType.startsWith("cv") || normalizedType.startsWith("resume") -> setOf("title", "type", "targetRole", "email", "phone", "location", "portfolio", "summary", "experience", "education", "skills", "notes")
                normalizedType.isBlank() -> setOf("title", "type", "documentNumber", "issueDate", "expiryDate", "member", "phone", "location", "notes")
                else -> setOf("title", "type", "name", "documentNumber", "issueDate", "expiryDate", "member", "phone", "address", "location", "notes")
            }
            "academics" -> if (normalizedType.contains("admission")) {
                setOf("title", "type", "institution", "studentName", "fatherName", "motherName", "dateOfBirth", "studentId", "roll", "registrationNumber", "phone", "address", "institutionAddress", "program", "admissionSession", "applicationNumber", "paymentAmount", "paymentDate", "notes")
            } else setOf("title", "type", "institution", "year", "grade", "program", "notes")
            "accounts" -> if (accountKind.equals("Bank Account", ignoreCase = true))
                setOf("title", "accountKind", "bankName", "accountHolder", "bankAccountType", "accountNumber", "currency", "branch", "routingNumber", "swiftCode", "iban", "mobileBanking", "website", "notes")
            else setOf("title", "accountKind", "accountType", "username", "email", "website", "registered", "status", "notes")
            "memberships" -> when {
                normalizedType.contains("student") -> setOf("title", "type", "organization", "studentName", "studentId", "program", "memberId", "startDate", "expiryDate", "level", "phone", "address", "website", "notes")
                normalizedType.contains("gym") -> setOf("title", "type", "organization", "memberId", "startDate", "expiryDate", "level", "phone", "website", "notes")
                else -> setOf("title", "type", "organization", "memberId", "startDate", "expiryDate", "level", "website", "notes")
            }
            "study" -> when {
                normalizedType == "link" -> setOf("title", "course", "subject", "materialType", "sourceUrl", "semester", "tags", "notes")
                normalizedType == "book" || normalizedType == "research paper" -> setOf("title", "course", "subject", "chapter", "materialType", "author", "publisher", "publicationDate", "sourceUrl", "semester", "tags", "notes")
                else -> setOf("title", "course", "subject", "chapter", "materialType", "author", "semester", "tags", "notes")
            }
            "personal-finance" -> when (financeType.lowercase()) {
                "income" -> setOf("title", "financeType", "amount", "currency", "transactionDate", "category", "counterparty", "accountReference", "notes")
                "expense" -> setOf("title", "financeType", "amount", "currency", "transactionDate", "category", "counterparty", "accountReference", "notes")
                "loan" -> setOf("title", "financeType", "amount", "currency", "transactionDate", "counterparty", "accountReference", "dueDate", "interestRate", "notes")
                "asset" -> setOf("title", "financeType", "amount", "currency", "transactionDate", "assetType", "accountReference", "notes")
                else -> setOf("title", "financeType", "amount", "currency", "notes")
            }
            else -> null
        }
        var result = if (keys == null) base else base.filter { it.key in keys }
        if (sectionId == "documents") {
            val numberLabel = when {
                normalizedType.contains("nid") || normalizedType.contains("national id") || normalizedType.contains("national identity") -> "NID number"
                normalizedType.contains("passport") -> "Passport number"
                normalizedType.contains("birth certificate") -> "Certificate number"
                normalizedType.contains("driving") || normalizedType.contains("licen") -> "Licence number"
                normalizedType.contains("tax") || normalizedType.contains("tin") -> "Tax ID / TIN"
                else -> "Document number"
            }
            result = result.map { field ->
                when (field.key) {
                    "documentNumber" -> field.copy(label = numberLabel)
                    "institutionName" -> field.copy(label = "Institute name")
                    "institutionAddress" -> field.copy(label = "Institute address")
                    "year" -> field.copy(label = if (normalizedType.contains("student")) "Academic year" else field.label)
                    else -> field
                }
            }
        }
        return (result + if (sectionId == "wallet-cards") emptyList() else listOf(additionalDataField)).distinctBy { it.key }
    }

    /** NAV_GROUPS from data.ts */
    val navGroups: List<Pair<String, List<String>>> = listOf(
        "Money" to listOf("personal-finance"),
        "Personal life" to listOf("documents", "academics", "family"),
        "Everyday" to listOf("subscriptions", "purchases", "accounts", "memberships", "wallet-cards"),
        "Knowledge & identity" to listOf("study", "notes", "business-card", "urls"),
    )

    val folderScopes = all.map { it.id } + listOf("contacts", "business-cards", "medical-records")
}
