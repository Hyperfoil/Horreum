package io.hyperfoil.tools.horreum.entity.json;

/**
 * Defines extra restrictions for read-only access.
 *
 * Do not change unless changing constants in SQL policies.
 */
public enum Access {
   /** Anyone can see */
   PUBLIC,
   /** Anyone who is authenticated (logged in) can see */
   PROTECTED,
   /** Only the owner can see */
   PRIVATE,
}
