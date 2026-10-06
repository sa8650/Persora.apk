package app.persora.android.core.util

import android.content.Context
import android.provider.ContactsContract
import app.persora.android.data.model.CONTACT_CATEGORIES
import app.persora.android.data.model.ContactPhone
import app.persora.android.data.model.PersoraContact
import java.text.Normalizer

/**
 * Contact import pipeline — a port of the vCard parsing, phone normalisation, duplicate detection and
 * invalid-number filtering in ContactsView.tsx, plus CSV (Google / Outlook / generic headers) and the
 * phone's own address book as sources. Pure Kotlin so it can be unit-tested; UI lives in ContactImportSheet.
 */
object ContactImport {
    /** A contact parsed from a source, before it is uploaded. [photo] is an optional embedded JPEG/PNG. */
    data class Draft(val contact: PersoraContact, val photo: ByteArray? = null, val photoMime: String = "image/jpeg")

    /** One row of the preview list. */
    data class Entry(
        val draft: Draft,
        val duplicateOf: String,           // name of the existing Persora contact it matches, or ""
        val invalidPhones: List<String>,
        val duplicatePhones: List<String>, // numbers already saved on other Persora contacts
        val selected: Boolean,
    ) {
        val name get() = draft.contact.name
        val hasUsableData get() = draft.contact.phoneNumbers.isNotEmpty() || draft.contact.email.isNotBlank()
    }

    data class Summary(val imported: Int, val skippedDuplicates: Int, val skippedInvalidNumbers: Int, val skippedDuplicateNumbers: Int, val failed: List<Pair<String, String>>)

    const val DEFAULT_COUNTRY = "+880"
    private const val MAX_PHOTO = 5L * 1024 * 1024

    /* ------------------------------------------------ phone helpers (ContactsView.tsx) ------------------------------------------------ */

    fun cleanDigits(value: String) = value.filter { it.isDigit() }

    fun normalizePhone(value: String, countryCode: String = DEFAULT_COUNTRY): String {
        val trimmed = value.trim()
        if (trimmed.startsWith("+")) return "+" + cleanDigits(trimmed)
        var digits = cleanDigits(trimmed)
        val prefix = cleanDigits(countryCode)
        if (digits.startsWith("00")) return "+" + digits.drop(2)
        if (prefix.isNotEmpty() && digits.startsWith(prefix)) return "+$digits"
        if (digits.startsWith("0")) digits = digits.drop(1)
        return if (digits.isNotEmpty()) "+$prefix$digits" else ""
    }

    /** Returns a normalised phone or null when the number is unusable (letters, too short/long…). */
    fun normalizeImportedPhone(value: String, label: String = "Mobile"): ContactPhone? {
        val raw = value.trim().removePrefix("tel:").removePrefix("TEL:")
        if (raw.isBlank() || !Regex("^\\+?[\\d\\s().-]+$").matches(raw)) return null
        val count = cleanDigits(raw).length
        if (count < 7 || count > 15) return null
        val number = normalizePhone(raw)
        val normalizedDigits = cleanDigits(number)
        if (normalizedDigits.length < 7 || normalizedDigits.length > 15) return null
        return ContactPhone(label, number)
    }

    /* ------------------------------------------------ duplicates ------------------------------------------------ */

    private fun normalizedName(v: String) = Normalizer.normalize(v, Normalizer.Form.NFKD).replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    private fun normalizedEmail(v: String) = v.trim().lowercase()

    fun duplicateKeys(c: PersoraContact): Set<String> {
        val keys = mutableSetOf<String>()
        if (normalizedEmail(c.email).isNotBlank()) keys += "email:" + normalizedEmail(c.email)
        c.phoneNumbers.forEach { p -> val d = cleanDigits(p.number); if (d.length >= 6) keys += "phone:$d" }
        val name = normalizedName(c.name)
        if (name.isNotBlank() && normalizedEmail(c.email).isBlank() && c.phoneNumbers.none { cleanDigits(it.number).length >= 6 }) keys += "name:$name|" + normalizedName(c.company)
        return keys
    }

    fun findDuplicate(c: PersoraContact, others: List<PersoraContact>): PersoraContact? {
        val keys = duplicateKeys(c)
        return others.firstOrNull { o -> duplicateKeys(o).any { it in keys } }
    }

    /** Same union-find grouping as findDuplicateGroups() on the web — used by the "possible duplicates" banner. */
    fun duplicateGroups(contacts: List<PersoraContact>): List<List<PersoraContact>> {
        val parent = IntArray(contacts.size) { it }
        fun find(i: Int): Int { var x = i; while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x] }; return x }
        fun union(a: Int, b: Int) { val ra = find(a); val rb = find(b); if (ra != rb) parent[rb] = ra }
        val seen = HashMap<String, Int>()
        contacts.forEachIndexed { i, c -> duplicateKeys(c).forEach { k -> val old = seen[k]; if (old != null) union(i, old) else seen[k] = i } }
        return contacts.indices.groupBy { find(it) }.values.map { idx -> idx.map { contacts[it] } }.filter { it.size > 1 }
    }

    /* ------------------------------------------------ preparing an import ------------------------------------------------ */

    /**
     * Validates numbers, drops ones already saved on other Persora contacts, de-duplicates within the batch and
     * flags whole-contact duplicates. Mirrors handleImportFile()/confirmImport() on the web.
     */
    fun prepare(drafts: List<Draft>, existing: List<PersoraContact>): List<Entry> {
        val seenNumbers = existing.flatMap { c -> c.phoneNumbers.mapNotNull { normalizeImportedPhone(it.number)?.number } }.toMutableSet()
        val out = ArrayList<Entry>(drafts.size)
        for (d in drafts) {
            val invalid = ArrayList<String>(); val dupNumbers = ArrayList<String>(); val phones = ArrayList<ContactPhone>()
            val local = HashSet<String>()
            for (p in d.contact.phoneNumbers) {
                val n = normalizeImportedPhone(p.number, p.label)
                if (n == null) { invalid += p.number; continue }
                if (n.number in seenNumbers) { dupNumbers += p.number; continue }
                if (!local.add(n.number)) { dupNumbers += p.number; continue }
                phones += n
            }
            val cleaned = d.contact.copy(name = d.contact.name.trim().ifBlank { "Unnamed contact" }, phoneNumbers = phones.take(20), email = d.contact.email.trim())
            val dupe = findDuplicate(cleaned, existing) ?: out.firstOrNull { e -> e.selected && duplicateKeys(e.draft.contact).any { it in duplicateKeys(cleaned) } }?.draft?.contact
            val selected = dupe == null && (phones.isNotEmpty() || cleaned.email.isNotBlank())
            if (selected) phones.forEach { seenNumbers += it.number } // later rows in the same batch see this one as saved
            out += Entry(Draft(cleaned, d.photo, d.photoMime), dupe?.name.orEmpty(), invalid, dupNumbers, selected)
        }
        return out
    }

    /* ------------------------------------------------ vCard ------------------------------------------------ */

    fun parseVcards(raw: String): List<Draft> {
        val text = raw.replace(Regex("\\r?\\n[ \\t]"), "").replace("\r", "")
        val chunks = Regex("BEGIN:VCARD[\\s\\S]*?END:VCARD", RegexOption.IGNORE_CASE).findAll(text).map { it.value }.toList()
        return chunks.mapNotNull { chunk ->
            val fields = HashMap<String, MutableList<Pair<String, String>>>() // key → (params, value)
            for (line in chunk.split('\n')) {
                val split = line.indexOf(':'); if (split < 0) continue
                val head = line.substring(0, split); val value = decodeValue(line.substring(split + 1), head)
                val key = head.split(';')[0].split('.').last().uppercase()
                val params = if (head.contains(';')) head.substring(head.indexOf(';') + 1) else ""
                fields.getOrPut(key) { mutableListOf() } += params to value
            }
            fun first(key: String) = fields[key]?.firstOrNull()?.second.orEmpty()
            val fn = first("FN")
            val n = first("N").split(';')
            val name = fn.ifBlank { listOf(n.getOrNull(1), n.getOrNull(0), n.getOrNull(2)).filter { !it.isNullOrBlank() }.joinToString(" ") }.ifBlank { "Unnamed contact" }
            val phones = (fields["TEL"] ?: emptyList()).map { (params, v) -> ContactPhone(phoneLabel(params), v.trim().removePrefix("tel:")) }.filter { it.number.isNotBlank() }.take(20)
            val emails = (fields["EMAIL"] ?: emptyList()).map { it.second.trim().removePrefix("mailto:") }.filter { it.isNotBlank() }
            val address = first("ADR").split(';').map { unescape(it) }.filter { it.isNotBlank() }.joinToString(", ")
            val notes = (listOf(first("NOTE")) + emails.drop(1).map { "Additional email: $it" }).filter { it.isNotBlank() }.joinToString("\n")
            val categories = first("CATEGORIES").split(',').map { unescape(it) }
            val category = CONTACT_CATEGORIES.firstOrNull { c -> categories.any { it.equals(c, true) } } ?: "Other"
            var photo: ByteArray? = null; var photoMime = "image/jpeg"
            fields["PHOTO"]?.firstOrNull { (p, v) -> v.isNotBlank() && (Regex("ENCODING=(b|base64)", RegexOption.IGNORE_CASE).containsMatchIn(p) || v.trim().startsWith("data:image/")) }?.let { (p, v) ->
                runCatching {
                    val data = v.trim()
                    val base64 = if (data.startsWith("data:")) data.substringAfter(",") else data
                    photoMime = if (data.startsWith("data:")) data.substringAfter("data:").substringBefore(";") else "image/" + (Regex("TYPE=([^;:]+)", RegexOption.IGNORE_CASE).find(p)?.groupValues?.get(1)?.lowercase() ?: "jpeg")
                    val bytes = android.util.Base64.decode(base64.filterNot { it.isWhitespace() }, android.util.Base64.DEFAULT)
                    if (bytes.size in 1..MAX_PHOTO.toInt()) photo = bytes
                }
            }
            val contact = PersoraContact(
                id = "", name = name, phoneNumbers = phones, email = emails.firstOrNull().orEmpty(),
                company = first("ORG").split(';').map { unescape(it) }.filter { it.isNotBlank() }.joinToString(" · "), jobTitle = first("TITLE"),
                address = address, birthday = parseBirthday(first("BDAY")), notes = notes, category = category,
            )
            if (contact.name.isBlank()) null else Draft(contact, photo, photoMime)
        }
    }

    private fun decodeValue(value: String, head: String): String {
        if (Regex("ENCODING=QUOTED-PRINTABLE", RegexOption.IGNORE_CASE).containsMatchIn(head)) {
            return runCatching {
                val bytes = java.io.ByteArrayOutputStream(); var i = 0
                while (i < value.length) {
                    val c = value[i]
                    if (c == '=' && i + 2 < value.length && value.substring(i + 1, i + 3).matches(Regex("[0-9A-Fa-f]{2}"))) { bytes.write(value.substring(i + 1, i + 3).toInt(16)); i += 3 }
                    else { bytes.write(c.code); i++ }
                }
                String(bytes.toByteArray(), Charsets.UTF_8)
            }.getOrElse { unescape(value) }
        }
        return unescape(value)
    }

    private fun unescape(v: String) = v.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    private fun phoneLabel(params: String): String {
        val type = (Regex("TYPE=([^;,:]+)", RegexOption.IGNORE_CASE).find(params)?.groupValues?.get(1) ?: params).split(',')[0].filter { it.isLetter() }.lowercase()
        return when {
            "home" in type -> "Home"; "work" in type -> "Work"; "main" in type -> "Main"; "whatsapp" in type -> "WhatsApp"
            "cell" in type || "mobile" in type -> "Mobile"; else -> "Other"
        }
    }

    private fun parseBirthday(value: String): String {
        val digits = value.filter { it.isDigit() }
        if (digits.length == 8) return "${digits.substring(0, 4)}-${digits.substring(4, 6)}-${digits.substring(6, 8)}"
        return if (Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(value)) value else ""
    }

    /* ------------------------------------------------ CSV (Google Contacts / Outlook / generic) ------------------------------------------------ */

    fun parseCsv(raw: String): List<Draft> {
        val rows = readCsv(raw.removePrefix("\uFEFF"))
        if (rows.size < 2) return emptyList()
        val header = rows.first().map { it.trim().lowercase() }
        fun col(vararg names: String): Int = names.map { it.lowercase() }.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } } ?: -1
        fun cols(regex: Regex): List<Int> = header.indices.filter { regex.matches(header[it]) }
        val iName = col("name", "full name", "display name", "contact name")
        val iFirst = col("given name", "first name", "first")
        val iMiddle = col("additional name", "middle name")
        val iLast = col("family name", "last name", "surname", "last")
        val iEmail = cols(Regex("e-?mail( ?\\d+)?( - value)?|e-mail address( ?\\d+)?|primary email|email address"))
        val iPhone = cols(Regex("phone( ?\\d+)?( - value)?|mobile( phone)?|mobile number|phone number|primary phone|home phone( ?\\d+)?|business phone( ?\\d+)?|work phone|telephone|tel|cell"))
        val iPhoneType = cols(Regex("phone ?\\d+ - type"))
        val iCompany = col("organization 1 - name", "organization name", "company", "organisation", "organization")
        val iTitle = col("organization 1 - title", "organization title", "job title", "title", "position")
        val iAddress = col("address 1 - formatted", "address", "home address", "home street", "business address", "street")
        val iBirthday = col("birthday", "birth date", "date of birth")
        val iNotes = col("notes", "note")
        val iCategory = col("category", "group membership", "categories", "labels")
        return rows.drop(1).mapNotNull { r ->
            fun at(i: Int) = if (i in r.indices) r[i].trim() else ""
            val name = at(iName).ifBlank { listOf(at(iFirst), at(iMiddle), at(iLast)).filter { it.isNotBlank() }.joinToString(" ") }
            val phones = iPhone.mapIndexedNotNull { k, i -> at(i).takeIf { it.isNotBlank() }?.let { v ->
                val typeCol = iPhoneType.getOrNull(k)
                val label = (typeCol?.let { at(it) } ?: header[i]).let { h -> when { "mobile" in h || "cell" in h -> "Mobile"; "home" in h -> "Home"; "work" in h || "business" in h -> "Work"; "main" in h -> "Main"; else -> "Mobile" } }
                v.split(":::", ";", " / ").map { it.trim() }.filter { it.isNotBlank() }.map { ContactPhone(label, it) }
            } }.flatten().take(20)
            val emails = iEmail.map { at(it) }.flatMap { it.split(":::", ";").map { e -> e.trim() } }.filter { it.isNotBlank() }
            if (name.isBlank() && phones.isEmpty() && emails.isEmpty()) return@mapNotNull null
            val categoryRaw = at(iCategory)
            val category = CONTACT_CATEGORIES.firstOrNull { c -> categoryRaw.split(":::", ",", ";").any { it.trim().equals(c, true) } } ?: "Other"
            Draft(PersoraContact(
                id = "", name = name.ifBlank { emails.firstOrNull() ?: phones.first().number }, phoneNumbers = phones, email = emails.firstOrNull().orEmpty(),
                company = at(iCompany), jobTitle = at(iTitle), address = at(iAddress), birthday = parseBirthday(at(iBirthday)),
                notes = (listOf(at(iNotes)) + emails.drop(1).map { "Additional email: $it" }).filter { it.isNotBlank() }.joinToString("\n"), category = category,
            ))
        }
    }

    /** RFC 4180-ish reader: quoted fields, doubled quotes, embedded newlines, comma / semicolon / tab delimiters. */
    private fun readCsv(text: String): List<List<String>> {
        val firstLine = text.lineSequence().firstOrNull().orEmpty()
        val delimiter = listOf(',', ';', '\t').maxByOrNull { d -> firstLine.count { it == d } } ?: ','
        val rows = ArrayList<List<String>>(); var row = ArrayList<String>(); val cell = StringBuilder(); var quoted = false; var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted -> if (c == '"') { if (i + 1 < text.length && text[i + 1] == '"') { cell.append('"'); i++ } else quoted = false } else cell.append(c)
                c == '"' -> quoted = true
                c == delimiter -> { row += cell.toString(); cell.setLength(0) }
                c == '\n' || c == '\r' -> { if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++; row += cell.toString(); cell.setLength(0); if (row.any { it.isNotBlank() }) rows += row; row = ArrayList() }
                else -> cell.append(c)
            }
            i++
        }
        row += cell.toString(); if (row.any { it.isNotBlank() }) rows += row
        return rows
    }

    /** Sniffs the content: vCard if it contains BEGIN:VCARD, otherwise CSV. */
    fun parseAny(text: String, fileName: String?): List<Draft> {
        val looksVcard = Regex("BEGIN:VCARD", RegexOption.IGNORE_CASE).containsMatchIn(text.take(4096)) || fileName?.lowercase()?.endsWith(".vcf") == true
        return if (looksVcard) parseVcards(text) else parseCsv(text)
    }

    /* ------------------------------------------------ phone address book ------------------------------------------------ */

    /** Reads the device address book (needs READ_CONTACTS). One Draft per aggregate contact with its numbers, e-mails, org and thumbnail. */
    fun readPhoneContacts(context: Context): List<Draft> {
        val cr = context.contentResolver
        data class Acc(var name: String = "", val phones: LinkedHashMap<String, String> = LinkedHashMap(), val emails: LinkedHashSet<String> = LinkedHashSet(), var company: String = "", var title: String = "", var photo: ByteArray? = null)
        val acc = LinkedHashMap<String, Acc>()
        val projection = arrayOf(
            ContactsContract.Data.CONTACT_ID, ContactsContract.Data.DISPLAY_NAME_PRIMARY, ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.DATA1, ContactsContract.Data.DATA2, ContactsContract.Data.DATA3, ContactsContract.Data.DATA4, ContactsContract.Data.DATA15,
        )
        val mimes = listOf(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
        val selection = mimes.joinToString(" OR ") { "${ContactsContract.Data.MIMETYPE}=?" }
        cr.query(ContactsContract.Data.CONTENT_URI, projection, selection, mimes.toTypedArray(), ContactsContract.Data.CONTACT_ID)?.use { c ->
            val iId = c.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID); val iName = c.getColumnIndexOrThrow(ContactsContract.Data.DISPLAY_NAME_PRIMARY)
            val iMime = c.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE); val i1 = c.getColumnIndexOrThrow(ContactsContract.Data.DATA1)
            val i2 = c.getColumnIndexOrThrow(ContactsContract.Data.DATA2); val i3 = c.getColumnIndexOrThrow(ContactsContract.Data.DATA3); val i4 = c.getColumnIndexOrThrow(ContactsContract.Data.DATA4); val i15 = c.getColumnIndexOrThrow(ContactsContract.Data.DATA15)
            while (c.moveToNext()) {
                val id = c.getString(iId) ?: continue
                val a = acc.getOrPut(id) { Acc() }
                if (a.name.isBlank()) a.name = c.getString(iName).orEmpty()
                when (c.getString(iMime)) {
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                        val number = c.getString(i1).orEmpty().trim(); if (number.isBlank()) continue
                        val label = when (c.getInt(i2)) {
                            ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"; ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
                            ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> "Main"; ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
                            ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM -> c.getString(i3)?.takeIf { it.isNotBlank() } ?: "Other"; else -> "Other"
                        }
                        a.phones.putIfAbsent(number, label)
                    }
                    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> c.getString(i1)?.trim()?.takeIf { it.isNotBlank() }?.let { a.emails += it }
                    ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> { if (a.company.isBlank()) a.company = c.getString(i1).orEmpty(); if (a.title.isBlank()) a.title = c.getString(i4).orEmpty() }
                    ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE -> if (a.photo == null) a.photo = runCatching { c.getBlob(i15) }.getOrNull()?.takeIf { it.isNotEmpty() }
                }
            }
        }
        return acc.values.filter { it.phones.isNotEmpty() || it.emails.isNotEmpty() }.map { a ->
            val emails = a.emails.toList()
            Draft(PersoraContact(
                id = "", name = a.name.ifBlank { a.phones.keys.first() }, phoneNumbers = a.phones.map { (n, l) -> ContactPhone(l, n) }.take(20), email = emails.firstOrNull().orEmpty(),
                company = a.company, jobTitle = a.title, notes = emails.drop(1).joinToString("\n") { "Additional email: $it" }, category = "Other",
            ), a.photo)
        }
    }
}
