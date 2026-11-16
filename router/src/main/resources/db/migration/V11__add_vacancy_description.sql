-- Free-text description of a vacancy. Nullable: most sources publish none on their listing
-- pages, and those that do are filled in by their crawlers as they are taught to read it.
ALTER TABLE vacancy ADD COLUMN description TEXT;
