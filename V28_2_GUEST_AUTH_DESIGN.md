# V28.2 guest sign-in / create-account redesign

- Replaces unstyled guest links at the bottom of the desktop sidebar and mobile drawer with a compact stacked authentication card.
- Strong primary Create account CTA, outlined secondary Sign in CTA, consistent icons, spacing, hover/focus states.
- Removes duplicate Sign in/Create account links from the primary navigation list.
- Mobile top bar Sign in button now has dedicated styling.
- Preserves existing Angular routes (`/login`, `/register`) and EN/Arabic translation calls.
- No backend or database changes.

## Check manually
1. Desktop signed-out: guest buttons at bottom of sidebar; links work.
2. Mobile signed-out: sign-in button in header and two actions in drawer; drawer closes when links are tapped.
3. Signed-in: existing profile and logout remain unchanged.
4. Arabic switch: labels render correctly and remain legible.
