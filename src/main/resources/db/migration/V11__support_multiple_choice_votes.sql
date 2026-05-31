ALTER TABLE votes DROP CONSTRAINT uk_vote_ballot_user;

ALTER TABLE votes ADD CONSTRAINT uk_vote_ballot_user_option UNIQUE (ballot_id, user_id, option_id);
